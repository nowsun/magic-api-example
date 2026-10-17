package org.ssssssss.example.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;

/**
 * 为 /auth/token 的接口描述动态注入 {@code magic-api.swagger.name}（如“OpenAPI接口”），
 * 并把其中提醒用户携带令牌的那句标红。
 *
 * <p>之所以用 springfox 的 {@link OperationBuilderPlugin} 而不是直接写在
 * {@code @ApiOperation(notes = ...)} 里，是因为注解值必须是编译期常量，无法引用
 * 运行时的配置项 {@code magic-api.swagger.name}。</p>
 */
@Component
public class TokenAuthOperationNotesPlugin implements OperationBuilderPlugin, Ordered {

	private final String swaggerName;

	public TokenAuthOperationNotesPlugin(
			@Value("${magic-api.swagger.name:OpenAPI接口}") String swaggerName) {
		this.swaggerName = swaggerName;
	}

	@Override
	public boolean supports(DocumentationType delimiter) {
		return DocumentationType.SWAGGER_2.equals(delimiter);
	}

	@Override
	public void apply(OperationContext context) {
		String pattern = context.requestMappingPattern();
		if (pattern != null && pattern.endsWith("/auth/token")) {
			context.operationBuilder().notes(
					"使用 AccessKey ID 和 Secret 换取短期访问令牌；"
							+ "<font color=\"red\">调用" + swaggerName + "时需在认证请求头中携带返回的令牌</font>");
		}
	}

	@Override
	public int getOrder() {
		// 放在所有 OperationBuilderPlugin 之后执行，确保覆盖注解里的描述
		return Ordered.LOWEST_PRECEDENCE;
	}
}
