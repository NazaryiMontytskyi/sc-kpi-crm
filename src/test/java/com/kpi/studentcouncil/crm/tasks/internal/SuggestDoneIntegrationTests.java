package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;

/** The suggestDone flag on the task view (detail and batched list) against a real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SuggestDoneIntegrationTests {

	@Autowired
	TaskService service;

	@Autowired
	JdbcTemplate jdbc;

	private UUID space;
	private UUID todo;
	private UUID done;
	private UUID user;
	private final List<UUID> tasks = new java.util.ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (UUID t : tasks) {
			jdbc.update("update resolution set parent_resolution_id = null where task_id = ?", t);
			jdbc.update("delete from resolution where task_id = ?", t);
			jdbc.update("delete from task where id = ?", t);
		}
		tasks.clear();
		if (space != null) {
			jdbc.update("delete from task_space_status where space_id = ?", space);
			jdbc.update("delete from task_space where id = ?", space);
			jdbc.update("delete from users where id = ?", user);
		}
		space = null;
	}

	private void fixture() {
		space = UUID.randomUUID();
		jdbc.update("insert into task_space (id, name, is_general, created_at, updated_at) values (?, ?, true, now(),"
				+ " now())", space, "Suggest " + space);
		todo = status("Todo", "TODO", 0);
		done = status("Done", "DONE", 1);
		user = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, 'x', 'ACTIVE', 'en', false, now(), now())", user,
				"sd-" + user.toString().substring(0, 8));
	}

	private UUID status(String name, String category, int position) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task_space_status (id, space_id, name, category, position, created_at, updated_at)"
				+ " values (?, ?, ?, ?, ?, now(), now())", id, space, name, category, position);
		return id;
	}

	private UUID task(UUID status) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into task (id, space_id, title, status_id, priority, created_at, updated_at) values (?, ?,"
				+ " 'Fixture', ?, 'NORMAL', now(), now())", id, space, status);
		tasks.add(id);
		return id;
	}

	private void resolution(UUID task, String state) {
		jdbc.update("insert into resolution (id, task_id, author_id, executor_id, instruction, due_date, state,"
				+ " created_at, updated_at) values (?, ?, ?, ?, 'Do it', current_date, ?, now(), now())",
				UUID.randomUUID(), task, user, user, state);
	}

	@Test
	void flagIsComputedForDetailAndBatchedList() {
		fixture();
		UUID accepted = task(todo);
		resolution(accepted, "ACCEPTED");
		resolution(accepted, "CANCELED");
		UUID pending = task(todo);
		resolution(pending, "ACCEPTED");
		resolution(pending, "IN_PROGRESS");
		UUID none = task(todo);
		UUID alreadyDone = task(done);
		resolution(alreadyDone, "ACCEPTED");

		assertThat(service.get(accepted).suggestDone()).isTrue();
		assertThat(service.suggestDone(accepted)).isTrue();
		assertThat(service.get(pending).suggestDone()).isFalse();
		assertThat(service.get(none).suggestDone()).isFalse();
		assertThat(service.get(alreadyDone).suggestDone()).isFalse();

		var page = service.list(new TaskService.TaskFilter(space, null, null, null, null, false), PageRequest.of(0, 20));
		assertThat(page.getContent()).hasSize(4);
		page.getContent().forEach(v -> assertThat(v.suggestDone()).isEqualTo(v.task().getId().equals(accepted)));
		// no automatic status change
		assertThat(jdbc.queryForObject("select status_id from task where id = ?", UUID.class, accepted)).isEqualTo(todo);
	}

}
