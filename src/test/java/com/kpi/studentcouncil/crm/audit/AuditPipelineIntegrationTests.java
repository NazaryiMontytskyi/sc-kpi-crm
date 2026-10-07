package com.kpi.studentcouncil.crm.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditPipelineIntegrationTests {

	@Autowired
	AuditPublisher publisher;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcTemplate jdbc;

	private long countFor(UUID entityId) {
		return jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Long.class, entityId);
	}

	@Test
	void publishingInsideTransactionPersistsExactlyOneRowWithJsonAndSystemActor() {
		UUID id = UUID.randomUUID();

		tx.executeWithoutResult(s -> publisher.publish(AuditAction.UPDATE, "Note", id,
				Map.of("title", "old"), Map.of("title", "new")));

		assertThat(countFor(id)).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap(
				"select actor_id, action, entity_type, before_state ->> 'title' as b, after_state ->> 'title' as a, "
						+ "at, ip from audit_log where entity_id = ?", id);
		assertThat(row.get("actor_id")).isEqualTo(ActorProvider.SYSTEM_ACTOR);
		assertThat(row.get("action")).isEqualTo("UPDATE");
		assertThat(row.get("entity_type")).isEqualTo("Note");
		assertThat(row.get("b")).isEqualTo("old");
		assertThat(row.get("a")).isEqualTo("new");
		assertThat(row.get("at")).isNotNull();
	}

	@Test
	void rollbackLeavesNoRow() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
			publisher.publish(AuditAction.CREATE, "Note", id, null, Map.of("title", "x"));
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(countFor(id)).isZero();
	}

	@Test
	void clientIpAndExplicitActorAreRecorded() {
		UUID id = UUID.randomUUID();
		UUID actor = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		try {
			tx.executeWithoutResult(s -> publisher
					.publish(new AuditEvent(AuditAction.LOGIN, "User", id, null, null, actor)));
		}
		finally {
			RequestContextHolder.resetRequestAttributes();
		}

		Map<String, Object> row = jdbc.queryForMap("select actor_id, ip from audit_log where entity_id = ?", id);
		assertThat(row.get("ip")).isEqualTo("203.0.113.7");
		assertThat(row.get("actor_id")).isEqualTo(actor);
	}

	@Test
	void databaseRejectsUpdateDeleteAndTruncate() {
		UUID id = UUID.randomUUID();
		tx.executeWithoutResult(s -> publisher.publish(AuditAction.CREATE, "Note", id, null, null));

		assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'DELETE' where entity_id = ?", id))
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from audit_log where entity_id = ?", id))
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.execute("truncate table audit_log")).hasMessageContaining("append-only");
		assertThat(countFor(id)).isEqualTo(1);
	}

	@Test
	void repositoryExposesNoUpdateOrDeleteMethods() throws Exception {
		Class<?> repo = Class.forName("com.kpi.studentcouncil.crm.audit.internal.AuditLogRepository");
		assertThat(repo.getMethods()).extracting(java.lang.reflect.Method::getName)
				.noneMatch(n -> n.toLowerCase().contains("delete") || n.toLowerCase().contains("update"));
	}

}
