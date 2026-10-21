package com.kpi.studentcouncil.crm.analytics.internal;

import org.springframework.stereotype.Service;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.tasks.PersonalResolutionSummary;

/**
 * Personal dashboard of the caller. Built only on public APIs of other modules. Extension point for the v2 shared
 * dashboard: add further sections to {@link DashboardDtos.MyDashboardResponse} or a sibling service; shared
 * statistics are deliberately not part of this class.
 */
@Service
class DashboardService {

	/** How many upcoming items the widget shows. */
	static final int UPCOMING_LIMIT = 5;

	private final PersonalResolutionSummary resolutions;
	private final ActorProvider actor;

	DashboardService(PersonalResolutionSummary resolutions, ActorProvider actor) {
		this.resolutions = resolutions;
		this.actor = actor;
	}

	DashboardDtos.MyDashboardResponse myDashboard() {
		PersonalResolutionSummary.Summary s = resolutions.summaryFor(actor.currentActorId(), UPCOMING_LIMIT);
		return new DashboardDtos.MyDashboardResponse(new DashboardDtos.MyResolutions(s.active(), s.overdue(),
				s.awaitingAcceptance(), s.upcoming().stream().map(u -> new DashboardDtos.UpcomingResolution(
						u.resolutionId(), u.taskId(), u.taskTitle(), u.dueDate(), u.state(), u.overdue())).toList()));
	}

}
