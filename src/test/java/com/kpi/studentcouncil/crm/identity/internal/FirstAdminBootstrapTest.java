package com.kpi.studentcouncil.crm.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditEvent;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.identity.FirstAdminCreated;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** First Admin bootstrap rules (INC-004), independent of any database state. */
class FirstAdminBootstrapTest {

	private final UserRepository users = mock(UserRepository.class);

	private final PasswordEncoder encoder = mock(PasswordEncoder.class);

	private final AuditPublisher audit = mock(AuditPublisher.class);

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private UserAccountService service;

	@BeforeEach
	void setUp() {
		service = new UserAccountService(users, encoder, new PasswordPolicy(10), audit, events, Clock.systemUTC());
		when(encoder.encode(any())).thenReturn("$2a$04$hash");
		when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void createsTheFirstAdminOnAnEmptyDatabaseWithMustChangePassword() {
		when(users.count()).thenReturn(0L);

		boolean created = service.createFirstAdminIfAbsent("  First.Admin ", "Bootstrap-pass-42");

		assertThat(created).isTrue();
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		User user = saved.getValue();
		assertThat(user.getLogin()).isEqualTo("first.admin");
		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getPasswordHash()).isEqualTo("$2a$04$hash").doesNotContain("Bootstrap-pass-42");
		verify(events).publishEvent(any(FirstAdminCreated.class));
		ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
		verify(audit).publish(event.capture());
		assertThat(event.getValue().action()).isEqualTo(AuditAction.CREATE);
	}

	@Test
	void doesNothingWhenAnyAccountAlreadyExists() {
		when(users.count()).thenReturn(1L);

		boolean created = service.createFirstAdminIfAbsent("another.admin", "Another-admin-pass-1");

		assertThat(created).isFalse();
		verify(users, never()).save(any(User.class));
		verify(audit, never()).publish(any(AuditEvent.class));
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void rejectsAPasswordThatViolatesThePolicy() {
		when(users.count()).thenReturn(0L);

		assertThatThrownBy(() -> service.createFirstAdminIfAbsent("first.admin", "short1"))
				.isInstanceOf(BusinessRuleException.class);
		verify(users, never()).save(any(User.class));
	}

}
