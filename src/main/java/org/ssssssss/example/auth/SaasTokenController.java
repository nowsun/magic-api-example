package org.ssssssss.example.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.model.JsonBean;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;

@Api(tags = "认证接口")
@RestController
@RequestMapping("/auth")
public class SaasTokenController {

	private final AccessTokenService accessTokenService;

	public SaasTokenController(AccessTokenService accessTokenService) {
		this.accessTokenService = accessTokenService;
	}

	@ApiOperation(value = "换取访问令牌", notes = "使用 AccessKey ID 和 Secret 换取短期访问令牌；调用动态 API 时需在认证请求头中携带返回的令牌")
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

	@ApiModel("令牌请求")
	public static class TokenRequest {
		@ApiModelProperty(value = "AccessKey ID", required = true)
		private String accessKeyId;
		@ApiModelProperty(value = "AccessKey Secret", required = true)
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
