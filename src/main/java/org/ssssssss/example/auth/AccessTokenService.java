package org.ssssssss.example.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AccessTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int SIGNATURE_LENGTH_BYTES = 16;
	private static final int MAX_CREDENTIAL_LENGTH = 256;
	private static final int MAX_TOKEN_LENGTH = 8192;

	private final AccessKeyRepository accessKeyRepository;
	private final ObjectMapper objectMapper;
	private final byte[] signingKey;
	private final long tokenTtlSeconds;

	public AccessTokenService(AccessKeyRepository accessKeyRepository,
							  ObjectMapper objectMapper,
							  SaasAuthProperties properties) {
		this.accessKeyRepository = accessKeyRepository;
		this.objectMapper = objectMapper;
		String configuredSigningKey = properties.getTokenSigningKey();
		if (!StringUtils.hasText(configuredSigningKey)
				|| configuredSigningKey.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("saas.auth.token-signing-key must contain at least 32 UTF-8 bytes");
		}
		if (properties.getTokenTtlSeconds() < 60 || properties.getTokenTtlSeconds() > 86400) {
			throw new IllegalStateException("saas.auth.token-ttl-seconds must be between 60 and 86400");
		}
		this.signingKey = configuredSigningKey.getBytes(StandardCharsets.UTF_8);
		this.tokenTtlSeconds = properties.getTokenTtlSeconds();
	}

	public IssuedToken issueToken(String accessKeyId, String accessKeySecret) {
		if (!validCredential(accessKeyId) || !validCredential(accessKeySecret)) {
			throw new InvalidAccessTokenException();
		}
		AccessKeyRepository.AccessKeyRecord record = accessKeyRepository.findActive(accessKeyId)
				.orElseThrow(InvalidAccessTokenException::new);
		if (!matchesSecret(accessKeySecret, record.getSecretHash())) {
			throw new InvalidAccessTokenException();
		}
		long expiresAt = System.currentTimeMillis() / 1000L + tokenTtlSeconds;
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("ak", accessKeyId);
		claims.put("t", record.getTenantId());
		claims.put("exp", expiresAt);
		try {
			String payload = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(objectMapper.writeValueAsBytes(claims));
			return new IssuedToken(payload + "." + sign(payload), tokenTtlSeconds);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to serialize access token claims", e);
		}
	}

	public long validateToken(String token) {
		if (!StringUtils.hasText(token) || token.length() > MAX_TOKEN_LENGTH) {
			throw new InvalidAccessTokenException();
		}
		String[] parts = token.split("\\.", -1);
		if (parts.length != 2 || !MessageDigest.isEqual(
				sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
				parts[1].getBytes(StandardCharsets.US_ASCII))) {
			throw new InvalidAccessTokenException();
		}
		try {
			byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
			JsonNode claims = objectMapper.readTree(payload);
			if (claims == null || !claims.isObject()) {
				throw new InvalidAccessTokenException();
			}
			String accessKeyId = textClaim(claims, "ak");
			JsonNode tenantIdClaim = claims.get("t");
			if (tenantIdClaim == null || !tenantIdClaim.isIntegralNumber()
					|| !tenantIdClaim.canConvertToLong()) {
				throw new InvalidAccessTokenException();
			}
			long tenantId = tenantIdClaim.longValue();
			JsonNode expiresAtClaim = claims.get("exp");
			if (expiresAtClaim == null || !expiresAtClaim.canConvertToLong()
					|| expiresAtClaim.asLong() <= System.currentTimeMillis() / 1000L) {
				throw new InvalidAccessTokenException();
			}
			Optional<AccessKeyRepository.AccessKeyRecord> record = accessKeyRepository.findActive(accessKeyId);
			if (!record.isPresent() || tenantId != record.get().getTenantId()) {
				throw new InvalidAccessTokenException();
			}
			return tenantId;
		} catch (IOException | IllegalArgumentException e) {
			throw new InvalidAccessTokenException();
		}
	}

	private String textClaim(JsonNode claims, String name) {
		JsonNode value = claims.get(name);
		if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
			throw new InvalidAccessTokenException();
		}
		return value.asText();
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
			byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(Arrays.copyOf(digest, SIGNATURE_LENGTH_BYTES));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to sign access token", e);
		}
	}

	private static boolean validCredential(String value) {
		return StringUtils.hasText(value) && value.length() <= MAX_CREDENTIAL_LENGTH;
	}

	private static boolean matchesSecret(String secret, String storedHash) {
		if (storedHash == null || storedHash.length() != 64) {
			return false;
		}
		byte[] expectedHash = new byte[32];
		for (int i = 0; i < expectedHash.length; i++) {
			int high = Character.digit(storedHash.charAt(i * 2), 16);
			int low = Character.digit(storedHash.charAt(i * 2 + 1), 16);
			if (high < 0 || low < 0) {
				return false;
			}
			expectedHash[i] = (byte) ((high << 4) + low);
		}
		try {
			byte[] actualHash = MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8));
			return MessageDigest.isEqual(expectedHash, actualHash);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	@ApiModel("令牌响应")
	public static final class IssuedToken {
		@ApiModelProperty(value = "短期访问令牌（Base64URL 载荷 + HMAC-SHA256 签名）",
				example = "eyJhayI6IjNiY2E2MGRiODc0ZjYxNzZhM2UxYTg3NyIsInQiOjM1NjEyMywiZXhwIjoxNzkyMjI2NjU0fQ.iIsvqBPscEV-CjfrumGtlg")
		private final String accessToken;
		@ApiModelProperty(value = "令牌有效期（秒）", example = "3600")
		private final long expiresIn;

		public IssuedToken(String accessToken, long expiresIn) {
			this.accessToken = accessToken;
			this.expiresIn = expiresIn;
		}

		public String getAccessToken() {
			return accessToken;
		}

		@ApiModelProperty(value = "令牌类型", example = "token")
		public String getTokenType() {
			return "token";
		}

		public long getExpiresIn() {
			return expiresIn;
		}
	}
}
