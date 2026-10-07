package com.kpi.studentcouncil.crm.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditEvent;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Session authentication API. State-changing calls need the CSRF token: the SPA reads it from the
 * {@code XSRF-TOKEN} cookie (set on every response, including the 401 of {@code GET /auth/me}) and echoes it in the
 * {@code X-XSRF-TOKEN} header.
 */
@ApiV1
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Session sign-in, sign-out and the current user")
class AuthController {

	private static final SecurityContextHolderStrategy HOLDER = SecurityContextHolder.getContextHolderStrategy();

	private final AuthService auth;

	private final UserAccountService accounts;

	private final PermissionResolver permissions;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository securityContextRepository;

	private final AuditPublisher audit;

	AuthController(AuthService auth, UserAccountService accounts, PermissionResolver permissions,
			SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository securityContextRepository,
			AuditPublisher audit) {
		this.auth = auth;
		this.accounts = accounts;
		this.permissions = permissions;
		this.sessionStrategy = sessionStrategy;
		this.securityContextRepository = securityContextRepository;
		this.audit = audit;
	}

	@PostMapping("/login")
	@Operation(summary = "Sign in with login and password; creates an HttpOnly session cookie",
			responses = {
					@ApiResponse(responseCode = "200", description = "Signed in"),
					@ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS (same answer for unknown login, wrong password, blocked or archived account)",
							content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "403", description = "CSRF_INVALID",
							content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "429", description = "LOGIN_LOCKED: too many failed attempts; see Retry-After",
							content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))) })
	MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
		CrmUserDetails principal;
		try {
			principal = auth.login(request.login(), request.password(), http.getRemoteAddr());
		}
		catch (LoginLockedException ex) {
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
			throw ex;
		}
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
				principal.getAuthorities());
		sessionStrategy.onAuthentication(authentication, http, response);
		SecurityContext context = HOLDER.createEmptyContext();
		context.setAuthentication(authentication);
		HOLDER.setContext(context);
		securityContextRepository.saveContext(context, http, response);
		return MeResponse.of(principal);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Sign out and invalidate the session (idempotent)")
	void logout(HttpServletRequest http, HttpServletResponse response) {
		Authentication authentication = HOLDER.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CrmUserDetails user) {
			audit.publish(new AuditEvent(AuditAction.LOGOUT, UserAccountService.ENTITY_TYPE, user.id(), null, null,
					user.id()));
		}
		new SecurityContextLogoutHandler().logout(http, response, authentication);
	}

	@GetMapping("/me")
	@Operation(summary = "The signed-in user (also answered while a password change is pending)",
			responses = { @ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "401",
					description = "UNAUTHORIZED; the response still carries the XSRF-TOKEN cookie",
					content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))) })
	MeResponse me() {
		return MeResponse.of(currentUser());
	}

	@PostMapping("/change-password")
	@Operation(summary = "Change the own password (required when mustChangePassword is true)",
			responses = {
					@ApiResponse(responseCode = "200", description = "Password changed; mustChangePassword is now false"),
					@ApiResponse(responseCode = "422", description = "CURRENT_PASSWORD_INVALID, PASSWORD_UNCHANGED or PASSWORD_POLICY_VIOLATION",
							content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "429", description = "LOGIN_LOCKED: too many wrong current passwords",
							content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))) })
	MeResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		CrmUserDetails user = currentUser();
		try {
			auth.changePassword(user.id(), http.getRemoteAddr(), request.currentPassword(), request.newPassword());
		}
		catch (LoginLockedException ex) {
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
			throw ex;
		}
		if (http.getSession(false) != null) {
			http.changeSessionId();
		}
		return refreshed(user);
	}

	@PatchMapping("/me/locale")
	@Operation(summary = "Persist the preferred UI language (uk or en)")
	MeResponse changeLocale(@Valid @RequestBody ChangeLocaleRequest request) {
		CrmUserDetails user = currentUser();
		accounts.changeLocale(user.id(), request.locale());
		return refreshed(user);
	}

	private MeResponse refreshed(CrmUserDetails user) {
		return MeResponse.of(CrmUserDetails.forSession(accounts.get(user.id()), permissions.permissionsOf(user.id())));
	}

	private static CrmUserDetails currentUser() {
		Authentication authentication = HOLDER.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CrmUserDetails user) {
			return user;
		}
		throw new UnauthenticatedException();
	}

}
