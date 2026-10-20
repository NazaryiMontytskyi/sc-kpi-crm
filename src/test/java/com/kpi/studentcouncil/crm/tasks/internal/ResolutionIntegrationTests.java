package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;

/** Persistence, history and transactional cascade of resolutions against a real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ResolutionIntegrationTests {

	@Autowired
	ResolutionService service;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mvc;

	@Autowired
	PasswordEncoder encoder;

	private static final String PASSWORD = "Sup3r-secret-pw";

	/** Signed-in security contexts per user; the service reads the actor from the thread's security context. */
	private final Map<UUID, SecurityContext> contexts = new java.util.HashMap<>();

	@MockitoSpyBean
	AuditPublisher audit;

	private final List<UUID> users = new ArrayList<>();
	private UUID space;
	private UUID task;

	@AfterEach
	void cleanUp() {
		Mockito.reset(audit);
		SecurityContextHolder.clearContext();
		contexts.clear();
		if (task != null) {
			List<UUID> ids = jdbc.queryForList("select id from resolution where task_id = ?", UUID.class, task);
			for (UUID id : ids) {
				jdbc.update("delete from resolution_history where resolution_id = ?", id);
				jdbc.update("delete from resolution_co_executor where resolution_id = ?", id);
			}
			jdbc.update("update resolution set parent_resolution_id = null where task_id = ?", task);
			jdbc.update("delete from resolution where task_id = ?", task);
			jdbc.update("delete from task where id = ?", task);
		}
		if (space != null) {
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
		users.clear();
		task = null;
		space = null;
	}

	private UUID user() {
		UUID id = UUID.randomUUID();
		users.add(id);
		String login = "res-user-" + id.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", id, login,
				encoder.encode(PASSWORD));
		try {
			MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
				request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
				return request;
			}).contentType(MediaType.APPLICATION_JSON)
					.content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
			assertThat(result.getResponse().getStatus()).isEqualTo(200);
			MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
			contexts.put(id, (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT"));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return id;
	}

	private void taskFixture() {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Resolutions " + space);
		UUID status = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, 'Todo', 'TODO', 0, now(), now())", status, space);
		task = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " 'Fixture', ?, 'NORMAL', now(), now())", task, space, status);
	}

	private void actAs(UUID user) {
		SecurityContextHolder.setContext(contexts.get(user));
	}

	private String state(UUID id) {
		return jdbc.queryForObject("select state from resolution where id = ?", String.class, id);
	}

	private long audits(UUID id, String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_log where entity_type = 'Resolution' and entity_id = ? and action = ?",
				Long.class, id, action);
	}

	@Test
	void aResolutionAndItsWholeLifecycleArePersistedWithOrderedHistoryAndAudit() {
		taskFixture();
		UUID author = user();
		UUID executor = user();
		UUID co = user();

		actAs(author);
		Resolution created = service.create(task, executor, List.of(co), "Organise the event",
				LocalDate.of(2030, 5, 1));
		UUID id = created.getId();
		actAs(executor);
		service.start(id);
		service.submit(id, "Booked the hall");
		actAs(author);
		service.requestRework(id, "Add a budget");
		actAs(executor);
		service.resume(id);
		service.submit(id, "Budget added");
		actAs(author);
		service.accept(id);

		Resolution loaded = service.get(id);
		assertThat(loaded.getState()).isEqualTo(ResolutionState.ACCEPTED);
		assertThat(loaded.getTaskId()).isEqualTo(task);
		assertThat(loaded.getAuthorId()).isEqualTo(author);
		assertThat(loaded.getExecutorId()).isEqualTo(executor);
		assertThat(loaded.getCoExecutorIds()).containsExactly(co);
		assertThat(loaded.getDueDate()).isEqualTo(LocalDate.of(2030, 5, 1));
		assertThat(loaded.getCompletionNote()).isEqualTo("Budget added");
		assertThat(loaded.getParentResolutionId()).isNull();
		assertThat(loaded.getHistory()).extracting(ResolutionHistoryEntry::to).containsExactly(
				ResolutionState.NEW, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED, ResolutionState.REWORK,
				ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED, ResolutionState.ACCEPTED);
		assertThat(loaded.getHistory()).extracting(ResolutionHistoryEntry::from).containsExactly(null,
				ResolutionState.NEW, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED, ResolutionState.REWORK,
				ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED);
		assertThat(loaded.getHistory()).extracting(ResolutionHistoryEntry::actorId).containsExactly(author, executor,
				executor, author, executor, executor, author);
		assertThat(loaded.getHistory().get(3).comment()).isEqualTo("Add a budget");
		assertThat(loaded.getHistory()).allMatch(h -> h.at() != null);
		assertThat(jdbc.queryForObject("select count(*) from resolution_history where resolution_id = ?", Long.class,
				id)).isEqualTo(7);
		assertThat(audits(id, "CREATE")).isEqualTo(1);
		assertThat(audits(id, "UPDATE")).isEqualTo(6);
		assertThat(service.listByTask(task)).extracting(Resolution::getId).containsExactly(id);
	}

	@Test
	void aTaskCanHaveSeveralResolutions() {
		taskFixture();
		UUID author = user();
		UUID e1 = user();
		UUID e2 = user();
		actAs(author);
		Resolution first = service.create(task, e1, List.of(), "One", LocalDate.of(2030, 5, 1));
		Resolution second = service.create(task, e2, List.of(), "Two", LocalDate.of(2030, 6, 1));

		assertThat(service.listByTask(task)).extracting(Resolution::getId)
				.containsExactlyInAnyOrder(first.getId(), second.getId());
	}

	@Test
	void aRejectedTransitionChangesNothingInTheDatabase() {
		taskFixture();
		UUID author = user();
		UUID executor = user();
		actAs(author);
		UUID id = service.create(task, executor, List.of(), "x", LocalDate.of(2030, 5, 1)).getId();

		actAs(author);
		assertThatThrownBy(() -> service.start(id)).hasMessageContaining("RESOLUTION_NOT_EXECUTOR");
		actAs(executor);
		assertThatThrownBy(() -> service.submit(id, "too early")).hasMessageContaining("TRANSITION_NOT_ALLOWED");
		service.start(id);
		assertThatThrownBy(() -> service.submit(id, " ")).hasMessageContaining("COMPLETION_NOTE_REQUIRED");

		assertThat(state(id)).isEqualTo("IN_PROGRESS");
		assertThat(service.get(id).getHistory()).hasSize(2);
	}

	/** Builds Head -> Deputy -> Chief -> Member chain through the persistence layer (redirect itself is INC-034). */
	private record Chain(UUID head, UUID deputy, UUID chief, UUID member, UUID r1, UUID r2, UUID r3, UUID r4) {
	}

	@Autowired
	ResolutionRepository repository;

	@Autowired
	org.springframework.transaction.support.TransactionTemplate tx;

	private Chain chain() {
		taskFixture();
		UUID head = user();
		UUID deputy = user();
		UUID chief = user();
		UUID member = user();
		java.time.Instant now = java.time.Instant.now();
		LocalDate due = LocalDate.of(2030, 5, 1);
		Chain chain = tx.execute(status -> {
			Resolution r1 = repository.save(new Resolution(task, head, deputy, java.util.Set.of(), "r1", due, null, now));
			Resolution r2 = repository
					.save(new Resolution(task, deputy, chief, java.util.Set.of(), "r2", due, r1.getId(), now));
			Resolution r3 = repository
					.save(new Resolution(task, chief, member, java.util.Set.of(), "r3", due, r2.getId(), now));
			Resolution r4 = repository
					.save(new Resolution(task, member, member, java.util.Set.of(), "r4", due, r3.getId(), now));
			r1.apply(ResolutionAction.START, deputy, null, now);
			r1.apply(ResolutionAction.REDIRECT, deputy, null, now);
			r2.apply(ResolutionAction.START, chief, null, now);
			r2.apply(ResolutionAction.REDIRECT, chief, null, now);
			r3.apply(ResolutionAction.START, member, null, now);
			r3.apply(ResolutionAction.REDIRECT, member, null, now);
			r4.apply(ResolutionAction.START, member, null, now);
			return new Chain(head, deputy, chief, member, r1.getId(), r2.getId(), r3.getId(), r4.getId());
		});
		return chain;
	}

	@Test
	void cancelingTheHeadResolutionCancelsTheWholeChainInOneTransactionWithHistoryAndAudit() {
		Chain c = chain();
		actAs(c.head());

		List<Resolution> changed = service.cancel(c.r1(), "no longer needed");

		assertThat(changed).extracting(Resolution::getId).containsExactly(c.r1(), c.r2(), c.r3(), c.r4());
		for (UUID id : List.of(c.r1(), c.r2(), c.r3(), c.r4())) {
			assertThat(state(id)).isEqualTo("CANCELED");
			assertThat(audits(id, "UPDATE")).isEqualTo(1);
		}
		Resolution r4 = service.get(c.r4());
		ResolutionHistoryEntry last = r4.getHistory().get(r4.getHistory().size() - 1);
		assertThat(last.to()).isEqualTo(ResolutionState.CANCELED);
		assertThat(last.from()).isEqualTo(ResolutionState.IN_PROGRESS);
		assertThat(last.actorId()).isEqualTo(c.head());
		assertThat(last.causedByResolutionId()).isEqualTo(c.r1());
		Resolution r1 = service.get(c.r1());
		assertThat(r1.getHistory().get(r1.getHistory().size() - 1).causedByResolutionId()).isNull();
	}

	@Test
	void aFailureInTheMiddleOfTheCascadeRollsEverythingBack() {
		Chain c = chain();
		actAs(c.head());
		// the audit of the third resolution fails after the first two have already been saved and audited
		doThrow(new IllegalStateException("audit down")).when(audit).publish(eq(AuditAction.UPDATE), eq("Resolution"),
				eq(c.r3()), any(), any());

		assertThatThrownBy(() -> service.cancel(c.r1(), "stop")).isInstanceOf(IllegalStateException.class);

		assertThat(state(c.r1())).isEqualTo("REDIRECTED");
		assertThat(state(c.r2())).isEqualTo("REDIRECTED");
		assertThat(state(c.r3())).isEqualTo("REDIRECTED");
		assertThat(state(c.r4())).isEqualTo("IN_PROGRESS");
		for (UUID id : List.of(c.r1(), c.r2(), c.r3(), c.r4())) {
			assertThat(service.get(id).getHistory().stream()
					.noneMatch(h -> h.to() == ResolutionState.CANCELED)).isTrue();
			assertThat(audits(id, "UPDATE")).isZero();
		}
	}

	@Test
	void finalDescendantsKeepTheirStateWhenTheParentIsCanceled() {
		Chain c = chain();
		// r4 gets accepted before the cancel; r4 author = member, executor = member
		tx.executeWithoutResult(status -> {
			Resolution r4 = repository.findById(c.r4()).orElseThrow();
			r4.apply(ResolutionAction.SUBMIT, c.member(), "done", java.time.Instant.now());
			r4.apply(ResolutionAction.ACCEPT, c.member(), null, java.time.Instant.now());
		});
		actAs(c.head());

		List<Resolution> changed = service.cancel(c.r1(), null);

		assertThat(changed).extracting(Resolution::getId).containsExactly(c.r1(), c.r2(), c.r3());
		assertThat(state(c.r4())).isEqualTo("ACCEPTED");
		assertThat(Map.of("n", service.get(c.r4()).getHistory().size())).containsEntry("n", 4);
	}

}
