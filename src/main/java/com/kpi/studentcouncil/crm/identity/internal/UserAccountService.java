package com.kpi.studentcouncil.crm.identity.internal;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditEvent;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.FirstAdminCreated;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Transactional account operations. Audit rows are written in the same transaction as the change and never
 * contain a password or hash.
 */
@Service
@Transactional
class UserAccountService {

	static final String ENTITY_TYPE = "User";

	static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final PasswordPolicy policy;

	private final AuditPublisher audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	UserAccountService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy, AuditPublisher audit,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.policy = policy;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	User get(UUID id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
	}

	/** Records a successful sign-in (LOGIN audit event, actor = the user). */
	User recordLogin(UUID userId) {
		User user = get(userId);
		user.markLoggedIn(clock.instant());
		audit.publish(new AuditEvent(AuditAction.LOGIN, ENTITY_TYPE, userId, null, null, userId));
		return user;
	}

	/**
	 * Changes the password of the signed-in user.
	 *
	 * @throws BusinessRuleException {@code CURRENT_PASSWORD_INVALID}, {@code PASSWORD_UNCHANGED} or
	 *                               {@code PASSWORD_POLICY_VIOLATION}
	 */
	User changePassword(UUID userId, String currentPassword, String newPassword) {
		User user = get(userId);
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessRuleException(CURRENT_PASSWORD_INVALID);
		}
		if (encoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessRuleException("PASSWORD_UNCHANGED");
		}
		policy.validate(newPassword, user.getLogin());
		var before = user.auditSnapshot();
		user.changePassword(encoder.encode(newPassword), clock.instant());
		audit.publish(new AuditEvent(AuditAction.PASSWORD_CHANGE, ENTITY_TYPE, userId, before, user.auditSnapshot(),
				userId));
		return user;
	}

	User changeLocale(UUID userId, String locale) {
		User user = get(userId);
		var before = user.auditSnapshot();
		user.changeLocale(locale);
		audit.publish(new AuditEvent(AuditAction.UPDATE, ENTITY_TYPE, userId, before, user.auditSnapshot(), userId));
		return user;
	}

	/**
	 * Creates the first account (must change the password on first sign-in) only if no account exists at all
	 * (archived ones included).
	 *
	 * @return true if the account was created
	 */
	boolean createFirstAdminIfAbsent(String rawLogin, String rawPassword) {
		if (users.count() > 0) {
			return false;
		}
		String login = User.normalizeLogin(rawLogin);
		policy.validate(rawPassword, login);
		User user = users.save(User.create(login, encoder.encode(rawPassword), "uk", true));
		audit.publish(new AuditEvent(AuditAction.CREATE, ENTITY_TYPE, user.getId(), null, user.auditSnapshot(),
				ActorProvider.SYSTEM_ACTOR));
		events.publishEvent(new FirstAdminCreated(user.getId()));
		return true;
	}

}
