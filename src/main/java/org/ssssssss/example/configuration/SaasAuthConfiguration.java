package org.ssssssss.example.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.ssssssss.example.auth.AccessKeyRepository;
import org.ssssssss.example.auth.AccessTokenService;
import org.ssssssss.example.auth.SaasAuthProperties;
import org.ssssssss.example.auth.SaasApiAuthenticationInterceptor;
import org.ssssssss.example.auth.SaasRequestInterceptor;
import org.ssssssss.magicapi.core.interceptor.AuthorizationInterceptor;
import org.ssssssss.magicapi.core.interceptor.DefaultAuthorizationInterceptor;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;

@Configuration
@EnableConfigurationProperties(SaasAuthProperties.class)
public class SaasAuthConfiguration {

	@Bean
	public AccessTokenService accessTokenService(AccessKeyRepository accessKeyRepository,
												 ObjectMapper objectMapper,
												 SaasAuthProperties properties) {
		return new AccessTokenService(accessKeyRepository, objectMapper, properties);
	}

	@Bean
	public RequestInterceptor saasRequestInterceptor() {
		return new SaasRequestInterceptor();
	}

	@Bean
	public WebMvcConfigurer saasApiAuthenticationConfigurer(AccessTokenService accessTokenService,
														   ObjectMapper objectMapper) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(new SaasApiAuthenticationInterceptor(accessTokenService, objectMapper))
						.addPathPatterns("/**");
			}
		};
	}

	@Bean
	public AuthorizationInterceptor magicApiAdminAuthorizationInterceptor(
			@Value("${magic-api.security.login:1}") String login,
			@Value("${magic-api.security.username:}") String username,
			@Value("${magic-api.security.password:}") String password) {
		if (!"0".equals(login) && !"1".equals(login)) {
			throw new IllegalStateException("magic-api.security.login must be 1 or 0");
		}
		if ("0".equals(login)) {
			return new DefaultAuthorizationInterceptor(null, null);
		}
		if (username.trim().isEmpty() || password.trim().isEmpty()) {
			throw new IllegalStateException("Set magic-api.security.username and password in cfg/magic-api.ini");
		}
		return new DefaultAuthorizationInterceptor(username, password);
	}
}
