package com.kpi.studentcouncil.crm.access.internal;

import static com.kpi.studentcouncil.crm.access.Permission.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.shared.seed.SeedScope;
import com.kpi.studentcouncil.crm.shared.seed.Seeder;

/**
 * Reference seed (CONTEXT.md section 5.2): the template roles. They are ordinary, non-system roles, so humans can
 * edit or archive them in the role builder. The seed runs once per environment (seed_history) and never touches a
 * role afterwards. The permission mapping is documented in {@code docs/seeds/roles.md} and awaits human review.
 */
@Component
class TemplateRoleSeeder implements Seeder {

	/** Base read permissions every role gets (openness by default). */
	static final List<Permission> VIEW = List.of(MEMBERS_VIEW, ORG_VIEW, TASKS_VIEW, KB_VIEW, DASHBOARD_VIEW);

	/** Template roles in display order: name to description and permissions. */
	static final Map<String, Template> TEMPLATES = templates();

	private final RoleService roles;

	private final RoleRepository repository;

	TemplateRoleSeeder(RoleService roles, RoleRepository repository) {
		this.roles = roles;
		this.repository = repository;
	}

	@Override
	public String id() {
		return "access.template-roles";
	}

	@Override
	public SeedScope scope() {
		return SeedScope.REFERENCE;
	}

	@Override
	public void run() {
		TEMPLATES.forEach((name, template) -> {
			if (!repository.existsByNameIgnoreCaseAndArchivedAtIsNull(name)) {
				roles.create(name, template.description(), template.keys());
			}
		});
	}

	record Template(String description, Set<String> keys) {
	}

	private static Map<String, Template> templates() {
		Map<String, Template> map = new LinkedHashMap<>();
		map.put("Head", template("Голова / Head of the Council",
				VIEW, MEMBERS_EDIT, ORG_MANAGE, POSITIONS_MANAGE, POSITIONS_APPOINT, ROLES_ASSIGN, TASKS_CREATE,
				TASKS_EDIT_ANY, TASKS_ARCHIVE, SPACES_MANAGE, RESOLUTIONS_CREATE, RESOLUTIONS_REDIRECT, KB_EDIT,
				KB_ARCHIVE, ONBOARDING_MANAGE, MEETINGS_MANAGE, POLLS_CREATE, VOTING_CAST, HANDOVER_PERFORM,
				AUDIT_VIEW));
		map.put("Deputy Head", template("Заступник голови / Deputy Head",
				VIEW, MEMBERS_EDIT, POSITIONS_APPOINT, TASKS_CREATE, TASKS_EDIT_ANY, TASKS_ARCHIVE, SPACES_MANAGE,
				RESOLUTIONS_CREATE, RESOLUTIONS_REDIRECT, KB_EDIT, KB_ARCHIVE, ONBOARDING_MANAGE, MEETINGS_MANAGE,
				POLLS_CREATE, VOTING_CAST));
		map.put("Head of Department", template("Керівник департаменту / Head of Department",
				VIEW, TASKS_CREATE, TASKS_EDIT_ANY, TASKS_ARCHIVE, SPACES_MANAGE, RESOLUTIONS_CREATE,
				RESOLUTIONS_REDIRECT, KB_EDIT, POLLS_CREATE, VOTING_CAST));
		map.put("Head of Division", template("Керівник відділу / Head of Division",
				VIEW, TASKS_CREATE, TASKS_EDIT_ANY, RESOLUTIONS_CREATE, RESOLUTIONS_REDIRECT, KB_EDIT));
		map.put("Member", template("Член Студентської ради / Council member",
				VIEW, TASKS_CREATE, RESOLUTIONS_CREATE, RESOLUTIONS_REDIRECT, KB_EDIT));
		map.put("HR", template("HR-департамент / HR department",
				VIEW, MEMBERS_CREATE, MEMBERS_EDIT, MEMBERS_ARCHIVE, MEMBERS_ACTIVITY_SET, POSITIONS_APPOINT,
				ROLES_ASSIGN, ONBOARDING_MANAGE, TASKS_CREATE, RESOLUTIONS_CREATE, RESOLUTIONS_REDIRECT, KB_EDIT));
		map.put("Advisor", template("Радник (перегляд і коментування) / Advisor (view and comment)", VIEW));
		return map;
	}

	private static Template template(String description, Object... parts) {
		List<Permission> all = new ArrayList<>();
		for (Object part : parts) {
			if (part instanceof Permission p) {
				all.add(p);
			}
			else {
				@SuppressWarnings("unchecked")
				List<Permission> list = (List<Permission>) part;
				all.addAll(list);
			}
		}
		return new Template(description,
				all.stream().map(Permission::key).collect(Collectors.toCollection(TreeSet::new)));
	}

}
