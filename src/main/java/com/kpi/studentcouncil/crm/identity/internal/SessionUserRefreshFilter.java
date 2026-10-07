package com.kpi.studentcouncil.crm.identity.internal;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-reads the signed-in account on every request (a primary-key lookup; ~80 users), so the database stays the
 * source of truth for sessions that are already open:
 * <ul>
 * <li>blocked/archived accounts lose their session immediately (the request becomes anonymous, so 401);</li>
 * <li>the principal always carries the current locale, permissions and {@code mustChangePassword};</li>
 * <li>while {@code mustChangePassword} is set, everything except {@code /me}, {@code /change-password} and
 * {@code /logout} is answered with 403 {@code PASSWORD_CHANGE_REQUIRED}.</li>
 * </ul>
 * Principals not created by this module (e.g. other authentication providers or test users) are left alone.
 */
class SessionUserRefreshFilter extends OncePerRequestFilter {

	static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_PENDING = Set.of("/api/v1/auth/me",
			"/api/v1/auth/change-password", "/api/v1/auth/logout");

	private final UserRepository users;

	private final PermissionResolver permissions;

	private final SecurityProblemWriter problems;

	SessionUserRefreshFilter(UserRepository users, PermissionResolver permissions, SecurityProblemWriter problems) {
		this.users = users;
		this.permissions = permissions;
		this.problems = problems;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = context.getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CrmUserDetails principal) {
			User user = users.findById(principal.id()).orElse(null);
			if (user == null || !user.canSignIn()) {
				SecurityContextHolder.clearContext();
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
				chain.doFilter(request, response);
				return;
			}
			CrmUserDetails fresh = CrmUserDetails.forSession(user, permissions.permissionsOf(user.getId()));
			context.setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
			if (fresh.mustChangePassword() && !ALLOWED_WHILE_PASSWORD_CHANGE_PENDING.contains(path(request))) {
				problems.write(request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private static String path(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
	}

}
