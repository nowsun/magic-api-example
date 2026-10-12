package org.ssssssss.example.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.RequestInterceptor;
import org.ssssssss.magicapi.core.model.JsonBean;

public class SaasRequestInterceptor implements RequestInterceptor {

	public static final String TENANT_ID_VARIABLE = "tenantId";

	@Override
	public Object preHandle(RequestEntity requestEntity) {
		Object tenantId = requestEntity.getRequest().getAttribute(TENANT_ID_VARIABLE);
		if (!(tenantId instanceof Long)) {
			return unauthorized();
		}
		requestEntity.getMagicScriptContext().set(TENANT_ID_VARIABLE, tenantId);
		return null;
	}

	private ResponseEntity<JsonBean<Void>> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new JsonBean<>(401, "Bearer token is missing, expired, or invalid"));
	}
}
