package com.kpi.studentcouncil.crm.access.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.access.Permission;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TemplateRoleSeederIntegrationTests {

	private static final List<String> NAMES = List.of("Head", "Deputy Head", "Head of Department", "Head of Division",
			"Member", "HR", "Advisor");

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TemplateRoleSeeder seeder;

	@Autowired
	RoleService roleService;

	private Set<String> permissionsOf(String name) {
		return Set.copyOf(jdbc.queryForList("select rp.permission_key from role_permission rp join role r on r.id = rp.role_id"
				+ " where r.name = ? and r.archived_at is null", String.class, name));
	}

	@Test
	void allSevenTemplateRolesExistAndAreNonSystem() {
		for (String name : NAMES) {
			Integer count = jdbc.queryForObject(
					"select count(*) from role where name = ? and is_system = false and archived_at is null",
					Integer.class, name);
			assertThat(count).as(name).isEqualTo(1);
			assertThat(permissionsOf(name)).as(name).containsAll(
					TemplateRoleSeeder.VIEW.stream().map(Permission::key).toList());
			assertThat(Permission.allKeys()).containsAll(permissionsOf(name));
		}
		assertThat(jdbc.queryForObject("select count(*) from seed_history where seed_id = 'access.template-roles'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void advisorAndHrPermissions() {
		assertThat(permissionsOf("Advisor")).doesNotContain("resolutions.create", "voting.cast")
				.hasSize(TemplateRoleSeeder.VIEW.size());
		assertThat(permissionsOf("HR")).contains("members.create", "members.edit", "members.archive",
				"members.activity.set");
		for (String name : List.of("Head", "Deputy Head", "Head of Department")) {
			assertThat(permissionsOf(name)).as(name).contains("voting.cast");
		}
		for (String name : List.of("Head of Division", "Member", "HR", "Advisor")) {
			assertThat(permissionsOf(name)).as(name).doesNotContain("voting.cast");
		}
	}

	@Test
	void seedingTwiceDoesNotDuplicate() {
		seeder.run();
		seeder.run();
		for (String name : NAMES) {
			assertThat(jdbc.queryForObject("select count(*) from role where lower(name) = lower(?)", Integer.class,
					name)).as(name).isEqualTo(1);
		}
	}

	@Test
	void seededRolesCanBeEditedAndArchived() {
		UUID id = jdbc.queryForObject("select id from role where name = 'Advisor'", UUID.class);
		roleService.update(id, "Advisor", "edited", Set.of("members.view"));
		assertThat(permissionsOf("Advisor")).containsExactly("members.view");
		roleService.archive(id);
		assertThat(jdbc.queryForObject("select archived_at is not null from role where id = ?", Boolean.class, id))
				.isTrue();
		// Restore the shared fixture for other tests.
		jdbc.update("update role set archived_at = null where id = ?", id);
		roleService.update(id, "Advisor", null, TemplateRoleSeeder.TEMPLATES.get("Advisor").keys());
	}

}
