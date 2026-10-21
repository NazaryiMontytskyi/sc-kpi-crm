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
	@Operation(summary = "My resolutions mini-dashboard",
			description = "Only for the caller: active and overdue resolutions I execute, resolutions I issued that "
					+ "await my acceptance (SUBMITTED), and up to 5 upcoming resolutions I execute (earliest due "
					+ "date first). Counts equal the personal resolution lists. Needs dashboard.view.")
	DashboardDtos.MyDashboardResponse myDashboard() {
		return service.myDashboard();
	}

}
