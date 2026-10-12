package org.ssssssss.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.ssssssss.magicapi.core.model.JsonBean;
import org.ssssssss.magicapi.core.web.RequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SaasApiAuthenticationInterceptor implements HandlerInterceptor {

	private final AccessTokenService accessTokenService;
	private final ObjectMapper objectMapper;

	public SaasApiAuthenticationInterceptor(AccessTokenService accessTokenService, ObjectMapper objectMapper) {
		this.accessTokenService = accessTokenService;
		this.objectMapper = objectMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
		if (!isMagicApiInvocation(handler)) {
			return true;
		}
		try {
			String token = request.getHeader("token");
			long tenantId = accessTokenService.validateToken(token);
			request.setAttribute(SaasRequestInterceptor.TENANT_ID_VARIABLE, tenantId);
			return true;
		} catch (InvalidAccessTokenException e) {
			return reject(response);
		}
	}

	private boolean reject(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), new JsonBean<>(401, "Bearer token is missing, expired, or invalid"));
		return false;
	}

	private boolean isMagicApiInvocation(Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return false;
		}
		HandlerMethod handlerMethod = (HandlerMethod) handler;
		return RequestHandler.class.isAssignableFrom(handlerMethod.getBeanType())
				&& "invoke".equals(handlerMethod.getMethod().getName());
	}
}
