package org.ssssssss.example.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Swagger 文档配置
 *
 * 用于把常规 Spring MVC 控制器（如 SaaS Token 认证接口）纳入 Knife4j 文档。
 * magic-api 动态接口由 magic-api-plugin-swagger 单独注册，与此处互不影响。
 */
@Configuration
public class SwaggerConfiguration {

	@Bean
	public Docket authApiDocket() {
		return new Docket(DocumentationType.SWAGGER_2)
				.groupName("Token认证")
				.select()
				.apis(RequestHandlerSelectors.basePackage("org.ssssssss.example.auth"))
				.paths(PathSelectors.any())
				.build()
				.apiInfo(apiInfo());
	}

	private ApiInfo apiInfo() {
		return new ApiInfoBuilder()
				.title("SaaS Token 认证接口")
				.description("AccessKey ID/Secret 换取短期访问令牌等认证接口")
				.version("1.0")
				.build();
	}
}
