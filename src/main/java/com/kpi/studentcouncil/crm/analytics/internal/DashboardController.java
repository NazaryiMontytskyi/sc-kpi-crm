package com.kpi.studentcouncil.crm.analytics.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Dashboard of the caller. There is no user id parameter: no statistics of other people are exposed. */
@ApiV1
@RequestMapping("/dashboard")
@Tag(name = "Dashboard")
class DashboardController {

	private final DashboardService service;

	DashboardController(DashboardService service) {
		this.service = service;
	}

	@GetMapping("/me")
	@PreAuthorize("hasAuthority('dashboard.view')")
	@Operation(summary = "My tasks mini-dashboard (My metrics)",
			description = "Only for the caller. tasks: active and overdue tasks I am an active assignee of (subtasks "
					+ "included; archived tasks and DONE/CANCELED status categories excluded; overdue = deadline before "
					+ "today in Europe/Kyiv) and up to 5 of them with the earliest deadlines. Counts equal the \"My "
					+ "tasks\" quick view. resolutions (deprecated, removed with resolutions): the same task data in the "
					+ "legacy shape; awaitingAcceptance is always 0. Needs dashboard.view.")
	DashboardDtos.MyDashboardResponse myDashboard() {
		return service.myDashboard();
	}

}
