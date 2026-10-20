package com.kpi.studentcouncil.crm.members.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crmtest.SharedAdmin;

/** Directory filters, search, status visibility, pagination and sort stability (fake data only). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberDirectoryIntegrationTests {

	private static final String PASSWORD = "Sup3r-secret-pw";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	MockHttpSession admin;

	/** Org rows created by a test; removed afterwards in FK order so other test classes find a clean org tree. */
	private final List<UUID> units = new ArrayList<>();

	private final List<UUID> positions = new ArrayList<>();

	private final List<UUID> users = new ArrayList<>();

	@org.junit.jupiter.api.AfterEach
	void cleanUpOrg() {
		for (UUID user : users) {
			jdbc.update("delete from org_appointment where user_id = ?", user);
			jdbc.update("delete from org_membership where user_id = ?", user);
		}
		for (UUID position : positions) {
			jdbc.update("delete from org_appointment where position_id = ?", position);
			jdbc.update("delete from org_position where id = ?", position);
		}
		for (UUID unit : units) {
			jdbc.update("delete from org_membership where org_unit_id = ?", unit);
		}
		for (UUID unit : units) {
			jdbc.update("update org_unit set parent_id = null where id = ?", unit);
		}
		for (UUID unit : units) {
			jdbc.update("delete from org_unit where id = ?", unit);
		}
		units.clear();
		positions.clear();
		users.clear();
	}

	@BeforeEach
	void setUp() throws Exception {
		SharedAdmin.ensure(jdbc, encoder);
		admin = signIn(SharedAdmin.LOGIN, SharedAdmin.PASSWORD);
	}

	private MockHttpSession signIn(String login, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf()).with(request -> {
			request.setRemoteAddr("10.8." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250)));
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	/** Letters only, so it is safe inside names and Telegram handles. */
	private static String tag() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8).replaceAll("[0-9]", "x");
	}

	private UUID faculty() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into faculty (id, code, name_uk, name_en, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", id, "D" + id.toString().substring(0, 8), "Тест " + id, "Test " + id);
		return id;
	}

	private UUID member(String lastName, String telegram, UUID faculty, String joinedAt, boolean archived,
			UUID level) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", id,
				"dir-" + id.toString().substring(0, 12), encoder.encode(PASSWORD));
		jdbc.update("insert into member_profile (id, user_id, last_name, first_name, faculty_id, study_group,"
				+ " telegram, joined_at, activity_level_id, created_at, updated_at, archived_at) values (?, ?, ?,"
				+ " 'Test', ?, 'XX-11', ?, ?::date, ?, now(), now(), " + (archived ? "now()" : "null") + ")",
				UUID.randomUUID(), id, lastName, faculty, telegram, joinedAt, level);
		users.add(id);
		return id;
	}

	private UUID unit(String type, UUID parent) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into org_unit (id, name, type, parent_id, created_at, updated_at) values (?, ?, ?, ?,"
				+ " now(), now())", id, "Unit " + id, type, parent);
		units.add(id);
		return id;
	}

	private void membership(UUID user, UUID unit) {
		jdbc.update("insert into org_membership (id, user_id, org_unit_id, since, created_at, updated_at) values"
				+ " (?, ?, ?, current_date, now(), now())", UUID.randomUUID(), user, unit);
	}

	private UUID position(UUID unit) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into org_position (id, title, org_unit_id, is_head, capacity, created_at, updated_at)"
				+ " values (?, ?, ?, false, 5, now(), now())", id, "Pos " + id, unit);
		positions.add(id);
		return id;
	}

	private void appointment(UUID position, UUID user, boolean ended) {
		jdbc.update("insert into org_appointment (id, position_id, user_id, start_date, end_date, created_at,"
				+ " updated_at) values (?, ?, ?, date '2025-01-01', " + (ended ? "date '2025-06-01'" : "null")
				+ ", now(), now())", UUID.randomUUID(), position, user);
	}

	private MvcResult list(MockHttpSession session, String query) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/members").session(session);
		for (String pair : query.split("&")) {
			String[] kv = pair.split("=", 2);
			request = request.param(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
					URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
		}
		return mvc.perform(request).andExpect(status().isOk()).andReturn();
	}

	private List<UUID> orderedIds(MockHttpSession session, String query) throws Exception {
		String json = list(session, query).getResponse().getContentAsString();
		List<String> raw = JsonPath.read(json, "$.content[*].id");
		return raw.stream().map(UUID::fromString).toList();
	}

	private Set<UUID> ids(MockHttpSession session, String query) throws Exception {
		return new HashSet<>(orderedIds(session, query));
	}

	@Test
	void everyFilterNarrowsResultsAndCombinationsAreAnd() throws Exception {
		String tag = tag();
		UUID fa = faculty();
		UUID fb = faculty();
		UUID level = UUID.randomUUID();
		UUID dept = unit("DEPARTMENT", null);
		UUID division = unit("DIVISION", dept);
		UUID other = unit("DEPARTMENT", null);
		UUID m1 = member("Zz" + tag + "One", "tgx" + tag + "one", fa, "2025-01-01", false, null);
		UUID m2 = member("Zz" + tag + "Two", "tgx" + tag + "two", fb, "2025-02-01", false, null);
		UUID m3 = member("Zz" + tag + "Three", "tgx" + tag + "three", fa, "2025-03-01", false, null);
		member("Zz" + tag + "Four", "tgx" + tag + "four", fa, "2025-04-01", true, null);
		UUID m5 = member("Zz" + tag + "Five", "tgx" + tag + "five", fa, "2025-05-01", false, level);
		membership(m1, dept);
		membership(m2, division);
		membership(m3, other);
		UUID pos = position(dept);
		appointment(pos, m2, false);
		appointment(pos, m1, true);
		String q = "q=" + tag;

		assertThat(ids(admin, q)).containsExactlyInAnyOrder(m1, m2, m3, m5);
		// a department filter includes the members of its divisions
		assertThat(ids(admin, q + "&departmentId=" + dept)).containsExactlyInAnyOrder(m1, m2);
		assertThat(ids(admin, q + "&divisionId=" + division)).containsExactly(m2);
		// only current holders of the position
		assertThat(ids(admin, q + "&positionId=" + pos)).containsExactly(m2);
		assertThat(ids(admin, q + "&facultyId=" + fb)).containsExactly(m2);
		assertThat(ids(admin, q + "&activityLevelId=" + level)).containsExactly(m5);
		// combinations are AND
		assertThat(ids(admin, q + "&departmentId=" + dept + "&facultyId=" + fa)).containsExactly(m1);
		assertThat(ids(admin, q + "&departmentId=" + dept + "&divisionId=" + division)).containsExactly(m2);
		assertThat(ids(admin, q + "&departmentId=" + other + "&divisionId=" + division)).isEmpty();
		assertThat(ids(admin, q + "&positionId=" + pos + "&facultyId=" + fa)).isEmpty();
		assertThat(ids(admin, q + "&departmentId=" + UUID.randomUUID())).isEmpty();
		// an ended membership does not count
		jdbc.update("update org_membership set until = current_date where user_id = ?", m2);
		assertThat(ids(admin, q + "&divisionId=" + division)).isEmpty();
	}

	@Test
	void searchIsCaseInsensitiveHandlesAtPrefixAndEscapesWildcards() throws Exception {
		String tag = tag();
		UUID fa = faculty();
		UUID m1 = member("Zz" + tag + "Kovalenko", "nick_" + tag + "_a", fa, "2025-01-01", false, null);
		UUID m2 = member("Zz" + tag + "Bondar", "nick" + tag + "_b", fa, "2025-01-01", false, null);
		assertThat(ids(admin, "q=" + tag.toUpperCase(Locale.ROOT))).containsExactlyInAnyOrder(m1, m2);
		assertThat(ids(admin, "q=kovalenko")).contains(m1).doesNotContain(m2);
		assertThat(ids(admin, "q=@NICK_" + tag + "_A")).containsExactly(m1);
		// LIKE wildcards in user input are literal
		assertThat(ids(admin, "q=" + tag + "%25")).isEmpty();
		assertThat(ids(admin, "q=nick%25" + tag)).isEmpty();
		assertThat(ids(admin, "q=nick_" + tag)).containsExactly(m1);
		// each word must match
		assertThat(ids(admin, "q=" + tag + "+Bondar")).containsExactly(m2);
		assertThat(ids(admin, "q=" + tag + "+Nobody")).isEmpty();
	}

	@Test
	void archivedMembersAreVisibleOnlyToArchiveHoldersEvenWithTheParameter() throws Exception {
		String tag = tag();
		UUID fa = faculty();
		UUID active = member("Zz" + tag + "Act", "tgx" + tag + "act", fa, "2025-01-01", false, null);
		UUID archived = member("Zz" + tag + "Arc", "tgx" + tag + "arc", fa, "2025-01-01", true, null);
		String q = "q=" + tag;
		assertThat(ids(admin, q)).containsExactly(active);
		assertThat(ids(admin, q + "&status=ARCHIVED")).containsExactly(archived);
		assertThat(ids(admin, q + "&status=ALL")).containsExactlyInAnyOrder(active, archived);
		assertThat(ids(admin, q + "&includeArchived=true")).containsExactlyInAnyOrder(active, archived);

		UUID user = UUID.randomUUID();
		String login = "dir-plain-" + user.toString().substring(0, 8);
		jdbc.update("insert into users (id, login, password_hash, status, locale, must_change_password, created_at,"
				+ " updated_at) values (?, ?, ?, 'ACTIVE', 'en', false, now(), now())", user, login,
				encoder.encode(PASSWORD));
		MockHttpSession plain = signIn(login, PASSWORD);
		assertThat(ids(plain, q + "&status=ALL")).containsExactly(active);
		assertThat(ids(plain, q + "&status=ARCHIVED")).containsExactly(active);
		assertThat(ids(plain, q + "&includeArchived=true")).containsExactly(active);
	}

	@Test
	void paginationAndSortAreStableWithTies() throws Exception {
		String tag = tag();
		UUID fa = faculty();
		Set<UUID> created = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			// identical last name, first name and join date: only the id tiebreaker orders them
			created.add(member("Zz" + tag, "tgx" + tag + "n" + i, fa, "2025-01-01", false, null));
		}
		for (String sort : List.of("lastName,asc", "lastName,desc", "joinedAt,asc", "firstName,desc")) {
			List<UUID> firstRun = pages(tag, sort);
			assertThat(firstRun).hasSize(5).doesNotHaveDuplicates();
			assertThat(new HashSet<>(firstRun)).isEqualTo(created);
			assertThat(pages(tag, sort)).isEqualTo(firstRun);
		}
		String json = list(admin, "q=" + tag + "&size=2&page=1").getResponse().getContentAsString();
		assertThat((Integer) JsonPath.read(json, "$.totalElements")).isEqualTo(5);
		assertThat((Integer) JsonPath.read(json, "$.totalPages")).isEqualTo(3);
		mvc.perform(get("/api/v1/members?sort=password,asc").session(admin)).andExpect(status().isUnprocessableEntity());
	}

	private List<UUID> pages(String tag, String sort) throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			all.addAll(orderedIds(admin, "q=" + tag + "&size=2&sort=" + sort + "&page=" + page));
		}
		return all;
	}

}
