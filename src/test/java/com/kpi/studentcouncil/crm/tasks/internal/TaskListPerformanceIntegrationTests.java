package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/**
 * Task list benchmark (INC-083, spec §7: "a task list of 2,000 tasks responds within 300 ms p95").
 *
 * <p><b>Fixture.</b> One general space with its default statuses; 2,000 tasks inserted with JDBC (1,600 top-level
 * tasks and 400 one-level subtasks, so {@code ix_task_parent} has real children); 40 fake users ({@code perf-*}
 * logins, no personal data); every task has one or two active assignees (about 3,000 {@code task_assignee} rows plus
 * removed rows that the filters must skip), the Admin is an assignee of every 10th task and the creator of all;
 * deadlines are spread from 90 days in the past to 90 days ahead (every 8th task has none), statuses cycle through
 * every category, every 5th task carries a label. {@code ANALYZE} runs after seeding, as autovacuum would on a
 * server, so the planner sees real statistics.
 *
 * <p><b>Method.</b> Each scenario is a typical tracker request through the full MVC stack (security filter chain,
 * permission check, controller, specification query with count, batched view queries for assignees, subtasks and
 * status categories, JSON serialization) with {@link MockMvc} against the Testcontainers PostgreSQL 16. Per scenario:
 * {@value #WARM_UP} warm-up requests (JIT, connection pool, Hibernate query plan cache), then {@value #ROUNDS} rounds
 * of {@value #SAMPLES} timed requests (a GC before each round); each round's p95 is the nearest-rank 95th percentile of
 * its samples. The asserted value is the <b>median of the per-round p95s</b>, which must be below
 * {@value #P95_LIMIT_MS} ms; if it is not, {@value #ROUNDS} more rounds are measured once and the median of all rounds
 * decides. A single noisy round (a loaded machine, GC, another container) therefore cannot fail the test, while a real
 * regression (every round slow) still does. The log shows every round's p95, the median and the slowest sample.
 *
 * <p><b>Indexes.</b> The indexes of INC-071 (V25: {@code ix_task_parent}, {@code ix_task_deadline},
 * {@code ix_task_creator}, {@code ix_task_assignee_user}, {@code ix_task_assignee_task},
 * {@code uq_task_assignee_active}) are asserted to <b>exist</b>. Only the assignee filter is asserted to <b>use</b> one
 * ({@code ix_task_assignee_user} in the logged {@code EXPLAIN ANALYZE}); at 2,000 rows PostgreSQL reads the
 * {@code task} side with a sequential scan, which is cheaper than an index there, so the other indexes are not
 * claimed to be used at this size. The SQL takes about 1-2 ms; latency is dominated by the HTTP/JPA overhead (MVC
 * stack, batched view queries, JSON).
 *
 * <p><b>Result</b> (developer laptop, Windows 10 + Docker Desktop, 2026-11-23; also recorded in ADR-0010): see
 * ADR-0010 "Hardening as verified" for the measured ranges, including a noisy run. No index was missing, so there is
 * no new migration.
 *
 * <p>Run alone: {@code ./mvnw test -Dtest=TaskListPerformanceIntegrationTests} (Docker required).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TaskIntegrationTests.EventsConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskListPerformanceIntegrationTests {

	private static final Logger log = LoggerFactory.getLogger(TaskListPerformanceIntegrationTests.class);

	static final int TASKS = 2_000;

	static final int SUBTASKS = 400;

	static final int USERS = 40;

	static final int WARM_UP = 30;

	static final int SAMPLES = 100;

	static final int ROUNDS = 3;

	static final long P95_LIMIT_MS = 300;

	private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	private MockHttpSession admin;

	private UUID adminId;

	private UUID space;

	private final List<UUID> users = new ArrayList<>();

	@BeforeAll
	void seed() throws Exception {
		adminId = SharedAdmin.ensure(jdbc, encoder);
		admin = signIn();
		space = UUID.fromString(JsonPath.read(mvc.perform(post("/api/v1/spaces").with(csrf()).session(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Perf space " + UUID.randomUUID() + "\",\"general\":true}")).andReturn()
				.getResponse().getContentAsString(), "$.id"));
		List<UUID> statuses = jdbc.queryForList("select id from task_space_status where space_id = ? order by position",
				UUID.class, space);
		assertThat(statuses).hasSizeGreaterThanOrEqualTo(3);

		String hash = encoder.encode("Perf-fake-pw-1");
		List<Object[]> userRows = new ArrayList<>();
		for (int i = 0; i < USERS; i++) {
			UUID id = UUID.randomUUID();
			users.add(id);
			userRows.add(new Object[] { id, "perf-" + id.toString().substring(0, 8), hash });
		}
		jdbc.batchUpdate("insert into users (id, login, password_hash, status, locale, must_change_password,"
				+ " created_at, updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", userRows);

		LocalDate today = LocalDate.now(KYIV);
		Instant base = Instant.now().minusSeconds(86_400L * 200);
		List<UUID> taskIds = new ArrayList<>();
		List<Object[]> taskRows = new ArrayList<>();
		List<Object[]> assigneeRows = new ArrayList<>();
		List<Object[]> labelRows = new ArrayList<>();
		for (int i = 0; i < TASKS; i++) {
			UUID id = UUID.randomUUID();
			taskIds.add(id);
			boolean subtask = i >= TASKS - SUBTASKS;
			UUID parent = subtask ? taskIds.get(i - (TASKS - SUBTASKS)) : null;
			LocalDate deadline = i % 8 == 0 ? null : today.plusDays((i % 181) - 90);
			Instant issued = base.plusSeconds(i * 600L);
			taskRows.add(new Object[] { id, space, "Perf fake task " + i, statuses.get(i % statuses.size()),
					deadline, java.sql.Timestamp.from(issued), adminId, java.sql.Timestamp.from(issued), parent });
			UUID first = users.get(i % USERS);
			assigneeRows.add(assignee(id, first, issued, false));
			if (i % 2 == 0) {
				assigneeRows.add(assignee(id, users.get((i + 7) % USERS), issued, false));
			}
			if (i % 4 == 0) {
				// a removed assignment the "active assignee" filters must skip
				assigneeRows.add(assignee(id, users.get((i + 13) % USERS), issued, true));
			}
			if (i % 10 == 0) {
				assigneeRows.add(assignee(id, adminId, issued, false));
			}
			if (i % 5 == 0) {
				labelRows.add(new Object[] { id, "perf" });
			}
		}
		jdbc.batchUpdate("insert into task (id, space_id, title, status_id, priority, deadline, custom_field_values,"
				+ " created_at, created_by, updated_at, creator_id, issued_at, parent_task_id) values (?, ?, ?, ?,"
				+ " 'NORMAL', ?, '{}'::jsonb, ?, ?, ?, ?, ?, ?)",
				taskRows.stream().map(r -> new Object[] { r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[5], r[6],
						r[7], r[8] }).toList());
		jdbc.batchUpdate("insert into task_assignee (id, task_id, user_id, assigned_by, assigned_at, removed_at,"
				+ " removed_by, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, now(), now())", assigneeRows);
		jdbc.batchUpdate("insert into task_label (task_id, label) values (?, ?)", labelRows);
		jdbc.execute("analyze task");
		jdbc.execute("analyze task_assignee");
		jdbc.execute("analyze task_label");
	}

	private Object[] assignee(UUID task, UUID user, Instant at, boolean removed) {
		java.sql.Timestamp ts = java.sql.Timestamp.from(at);
		return new Object[] { UUID.randomUUID(), task, user, adminId, ts, removed ? ts : null,
				removed ? adminId : null };
	}

	@AfterAll
	void cleanUp() {
		String inSpace = "(select id from task where space_id = ?)";
		jdbc.update("delete from task_assignee where task_id in " + inSpace, space);
		jdbc.update("delete from task_label where task_id in " + inSpace, space);
		jdbc.update("delete from task where space_id = ? and parent_task_id is not null", space);
		jdbc.update("delete from task where space_id = ?", space);
		jdbc.update("delete from task_space_status where space_id = ?", space);
		jdbc.update("delete from task_space where id = ?", space);
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", user);
		}
	}

	private MockHttpSession signIn() throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.83." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"" + SharedAdmin.LOGIN + "\",\"password\":\""
				+ SharedAdmin.PASSWORD + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void listIndexesOfInc071Exist() {
		List<String> indexes = jdbc.queryForList("select indexname from pg_indexes where tablename in ('task',"
				+ " 'task_assignee')", String.class);
		assertThat(indexes).contains("ix_task_parent", "ix_task_deadline", "ix_task_creator", "ix_task_space",
				"ix_task_assignee_user", "ix_task_assignee_task", "uq_task_assignee_active");
	}

	@Test
	void taskListOf2000TasksRespondsWithin300msP95() throws Exception {
		UUID someone = users.get(3);
		Map<String, String> scenarios = new LinkedHashMap<>();
		scenarios.put("default (newest first)", "/tasks?page=0&size=20");
		scenarios.put("space, by deadline, page 5", "/tasks?spaceId=" + space + "&sort=deadline,asc&page=5&size=20");
		scenarios.put("assignee", "/tasks?assignee=" + someone + "&page=0&size=20");
		scenarios.put("assignee + overdue", "/tasks?assignee=" + someone + "&overdue=true&page=0&size=20");
		scenarios.put("overdue", "/tasks?overdue=true&page=0&size=20");
		scenarios.put("view MY_TASKS, 100 per page", "/tasks?view=MY_TASKS&page=0&size=100");
		scenarios.put("view ISSUED_BY_ME, page 10", "/tasks?view=ISSUED_BY_ME&page=10&size=20");
		scenarios.put("subtasks of a task", "/tasks?parentId=" + firstParentWithChildren() + "&page=0&size=20");
		scenarios.put("label + deadline range", "/tasks?labels=perf&deadlineFrom=" + LocalDate.now(KYIV).minusDays(30)
				+ "&deadlineTo=" + LocalDate.now(KYIV).plusDays(30) + "&page=0&size=20");

		// the fixture is really there and the filters really narrow it
		assertThat(total("/tasks?spaceId=" + space + "&size=1")).isEqualTo(TASKS);
		assertThat(total("/tasks?assignee=" + someone + "&spaceId=" + space + "&size=1")).isBetween(40L, 120L);

		StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%n%-32s %-26s %10s %8s%n", "scenario",
				"p95 per round (ms)", "median p95", "max ms"));
		Map<String, Double> medians = new LinkedHashMap<>();
		for (Map.Entry<String, String> s : scenarios.entrySet()) {
			for (int i = 0; i < WARM_UP; i++) {
				call(s.getValue());
			}
			List<Double> rounds = new ArrayList<>();
			double max = 0;
			for (int r = 0; r < ROUNDS; r++) {
				max = Math.max(max, round(s.getValue(), rounds));
			}
			double median = median(rounds);
			if (median >= P95_LIMIT_MS) {
				// one retry against a passing disturbance (GC, other containers): ROUNDS more rounds, median of all
				log.warn("Task list benchmark: {} median p95 {} ms over the limit, measuring {} more rounds",
						s.getKey(), median, ROUNDS);
				for (int r = 0; r < ROUNDS; r++) {
					max = Math.max(max, round(s.getValue(), rounds));
				}
				median = median(rounds);
			}
			medians.put(s.getKey(), median);
			table.append(String.format(Locale.ROOT, "%-32s %-26s %10.1f %8.1f%n", s.getKey(), rounds.stream()
					.map(p -> String.format(Locale.ROOT, "%.1f", p)).toList(), median, max));
		}
		log.info("Task list benchmark: {} tasks ({} subtasks), {} warm-up + {} rounds x {} samples per scenario{}",
				TASKS, SUBTASKS, WARM_UP, ROUNDS, SAMPLES, table);

		List<String> plan = jdbc.queryForList("explain (analyze, costs off) select t.id from task t where"
				+ " t.archived_at is null and exists (select 1 from task_assignee a where a.task_id = t.id and"
				+ " a.removed_at is null and a.user_id = ?) order by t.created_at desc limit 20", String.class, someone);
		log.info("Plan of the assignee lookup:\n{}", String.join("\n", plan));
		// the assignee filter really uses the INC-071 index (the task side is a cheap scan at this size)
		assertThat(String.join("\n", plan)).contains("ix_task_assignee_user");

		assertThat(medians).allSatisfy(
				(name, p95) -> assertThat(p95).as(name + " median of per-round p95 (ms)").isLessThan(P95_LIMIT_MS));
	}

	/** One round of {@value #SAMPLES} timed requests: adds its p95 to {@code p95s}, returns its slowest sample. */
	private double round(String path, List<Double> p95s) throws Exception {
		System.gc();
		double[] ms = new double[SAMPLES];
		for (int i = 0; i < SAMPLES; i++) {
			long start = System.nanoTime();
			call(path);
			ms[i] = (System.nanoTime() - start) / 1_000_000.0;
		}
		Arrays.sort(ms);
		p95s.add(percentile(ms, 95));
		return ms[ms.length - 1];
	}

	static double median(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
	}

	private UUID firstParentWithChildren() {
		return jdbc.queryForObject("select parent_task_id from task where space_id = ? and parent_task_id is not null"
				+ " limit 1", UUID.class, space);
	}

	private void call(String path) throws Exception {
		MvcResult result = mvc.perform(get("/api/v1" + path).session(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andReturn();
		if (result.getResponse().getStatus() != 200) {
			throw new AssertionError(path + " -> " + result.getResponse().getStatus() + " "
					+ result.getResponse().getContentAsString());
		}
	}

	private long total(String path) throws Exception {
		String body = mvc.perform(get("/api/v1" + path).session(admin)).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.totalElements")).longValue();
	}

	/** Nearest-rank percentile of sorted samples. */
	static double percentile(double[] sorted, int p) {
		int rank = (int) Math.ceil(p / 100.0 * sorted.length);
		return sorted[Math.max(0, rank - 1)];
	}

}
