package org.ssssssss.example.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.model.JsonBean;

@RestController
@RequestMapping("/auth")
public class SaasTokenController {

	private final AccessTokenService accessTokenService;

	public SaasTokenController(AccessTokenService accessTokenService) {
		this.accessTokenService = accessTokenService;
	}

	@PostMapping("/token")
	public ResponseEntity<?> issueToken(@RequestBody TokenRequest request) {
		try {
			return ResponseEntity.ok(accessTokenService.issueToken(
					request.getAccessKeyId(), request.getAccessKeySecret()));
		} catch (InvalidAccessTokenException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new JsonBean<>(401, "Invalid access credentials"));
		}
	}

	public static class TokenRequest {
		private String accessKeyId;
		private String accessKeySecret;

		public String getAccessKeyId() {
			return accessKeyId;
		}

		public void setAccessKeyId(String accessKeyId) {
			this.accessKeyId = accessKeyId;
		}

		public String getAccessKeySecret() {
			return accessKeySecret;
		}

		public void setAccessKeySecret(String accessKeySecret) {
			this.accessKeySecret = accessKeySecret;
		}
	}
}
