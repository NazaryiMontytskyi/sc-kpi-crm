package com.kpi.studentcouncil.crm.identity.internal;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata and the session-cookie security scheme (the spec is the contract for the generated client). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String SESSION_SCHEME = "sessionCookie";

	@Bean
	OpenAPI crmOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Student Council of KPI CRM API").version("v1")
						.description("Session cookie authentication. State-changing requests must send the "
								+ "XSRF-TOKEN cookie value in the X-XSRF-TOKEN header. Errors are RFC 7807 "
								+ "application/problem+json with a stable `code`."))
				.components(new Components().addSecuritySchemes(SESSION_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
								.name("CRMSESSION")))
				.addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
	}

}
