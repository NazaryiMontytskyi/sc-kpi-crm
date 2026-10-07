package com.kpi.studentcouncil.crm.identity.internal;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.servlet.LocaleResolver;

import tools.jackson.databind.json.JsonMapper;

/**
 * Security baseline (CONTEXT.md section 6): server-side session in an HttpOnly cookie, CSRF cookie for the SPA,
 * everything authenticated except sign-in/sign-out, health and (dev/test only) the OpenAPI documents.
 * 401/403 produced here are {@code application/problem+json}.
 * <p>
 * Method security ({@code @EnableMethodSecurity}, {@code @PreAuthorize}) is wired by the access module (INC-005);
 * the principal already carries one {@code GrantedAuthority} per permission key (see {@link PermissionResolver}).
 * <p>
 * OpenAPI/Swagger UI: public only when {@code crm.identity.public-api-docs=true} (dev and test profiles). Otherwise
 * they require authentication, and the prod profile disables springdoc entirely (see application.yml).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

	private static final String API = "/api/v1/auth";

	@Bean
	SecurityProblemWriter securityProblemWriter(MessageSource messages, LocaleResolver localeResolver,
			JsonMapper jsonMapper) {
		return new SecurityProblemWriter(messages, localeResolver, jsonMapper);
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax"));
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** Applied by the login endpoint: new session id (fixation protection) and a fresh CSRF token. */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		SessionAuthenticationStrategy delegate = new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
		return (authentication, request, response) -> {
			delegate.onAuthentication(authentication, request, response);
			// The rotated CSRF token is created lazily; materialise it so the new cookie is sent with this response.
			if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
				token.getToken();
			}
		};
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
			CsrfTokenRepository csrfTokenRepository, SecurityContextRepository securityContextRepository,
			SecurityProblemWriter problems, UserRepository users, PermissionResolver permissions,
			IdentityProperties properties) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> problems.write(request, response,
				HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
		AccessDeniedHandler deniedHandler = (request, response, ex) -> problems.write(request, response,
				HttpStatus.FORBIDDEN, ex instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN");

		http.authenticationManager(authenticationManager)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.addFilterBefore(new SessionUserRefreshFilter(users, permissions, problems), AuthorizationFilter.class)
				.authorizeHttpRequests(auth -> {
					auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					auth.requestMatchers(HttpMethod.POST, API + "/login", API + "/logout").permitAll();
					auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
					if (properties.publicApiDocs()) {
						auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
								.permitAll();
					}
					auth.anyRequest().authenticated();
				});
		return http.build();
	}

}
