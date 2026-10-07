package com.kpi.studentcouncil.crm.identity.internal;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditEvent;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/**
 * Sign-in orchestration: throttling, authentication through the (pluggable) {@link AuthenticationManager},
 * auditing. Deliberately not transactional, so LOGIN_FAILED is persisted even though the request fails.
 */
@Service
class AuthService {

	private final AuthenticationManager authenticationManager;

	private final LoginThrottle throttle;

	private final UserRepository users;

	private final UserAccountService accounts;

	private final PermissionResolver permissions;

	private final AuditPublisher audit;

	AuthService(AuthenticationManager authenticationManager, LoginThrottle throttle, UserRepository users,
			UserAccountService accounts, PermissionResolver permissions, AuditPublisher audit) {
		this.authenticationManager = authenticationManager;
		this.throttle = throttle;
		this.users = users;
		this.accounts = accounts;
		this.permissions = permissions;
		this.audit = audit;
	}

	/**
	 * Verifies the credentials and returns the session principal.
	 *
	 * @throws LoginLockedException        too many failed attempts for this login+IP
	 * @throws InvalidCredentialsException any other failure (identical for unknown login, wrong password, blocked,
	 *                                     archived)
	 */
	CrmUserDetails login(String rawLogin, String password, String clientIp) {
		String login = User.normalizeLogin(rawLogin);
		String key = login + "|" + clientIp;
		throttle.checkAllowed(key);
		try {
			if (password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
				throw new BadCredentialsException("too long");
			}
			Authentication result = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, password));
			if (!(result.getPrincipal() instanceof CrmUserDetails principal)) {
				throw new BadCredentialsException("unsupported principal");
			}
			throttle.reset(key);
			User user = accounts.recordLogin(principal.id());
			return CrmUserDetails.forSession(user, permissions.permissionsOf(user.getId()));
		}
		catch (AuthenticationException ex) {
			throttle.recordFailure(key);
			auditFailure(login);
			throw new InvalidCredentialsException();
		}
	}

	/** Changes the signed-in user's password; wrong current passwords are throttled like sign-in failures. */
	void changePassword(UUID userId, String clientIp, String current, String next) {
		String key = "pwd:" + userId + "|" + clientIp;
		throttle.checkAllowed(key);
		try {
			accounts.changePassword(userId, current, next);
			throttle.reset(key);
		}
		catch (BusinessRuleException ex) {
			if (UserAccountService.CURRENT_PASSWORD_INVALID.equals(ex.getCode())) {
				throttle.recordFailure(key);
			}
			throw ex;
		}
	}

	private void auditFailure(String login) {
		Optional<User> user = users.findByLogin(login);
		// The login string is never stored for unknown accounts (it may be a mistyped password).
		Map<String, Object> after = Map.of("reason",
				user.map(u -> u.canSignIn() ? "BAD_CREDENTIALS" : "ACCOUNT_" + u.getStatus().name())
						.orElse("UNKNOWN_LOGIN"));
		UUID id = user.map(User::getId).orElse(null);
		audit.publish(new AuditEvent(AuditAction.LOGIN_FAILED, UserAccountService.ENTITY_TYPE, id, null, after,
				id != null ? id : ActorProvider.SYSTEM_ACTOR));
	}

}
