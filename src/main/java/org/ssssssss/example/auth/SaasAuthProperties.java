package org.ssssssss.example.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas.auth")
public class SaasAuthProperties {

	private String tokenSigningKey;
	private long tokenTtlSeconds = 3600;

	public String getTokenSigningKey() {
		return tokenSigningKey;
	}

	public void setTokenSigningKey(String tokenSigningKey) {
		this.tokenSigningKey = tokenSigningKey;
	}

	public long getTokenTtlSeconds() {
		return tokenTtlSeconds;
	}

	public void setTokenTtlSeconds(long tokenTtlSeconds) {
		this.tokenTtlSeconds = tokenTtlSeconds;
	}
}
