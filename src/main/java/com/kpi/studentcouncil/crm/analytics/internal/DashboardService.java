package com.kpi.studentcouncil.crm.analytics.internal;

import java.util.List;

import org.springframework.stereotype.Service;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.tasks.PersonalTaskSummary;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * Personal dashboard of the caller. Built only on public APIs of other modules: "My metrics" and "My tasks" come
 * from {@link PersonalTaskSummary} (tasks via assignees, RR-5). Extension point for the v2 shared dashboard: add
 * further sections to {@link DashboardDtos.MyDashboardResponse} or a sibling service; shared statistics are
 * deliberately not part of this class.
 */
@Service
class DashboardService {

	/** How many upcoming items the widget shows. */
	static final int UPCOMING_LIMIT = 5;

	private final PersonalTaskSummary tasks;
	private final ActorProvider actor;

	DashboardService(PersonalTaskSummary tasks, ActorProvider actor) {
		this.tasks = tasks;
		this.actor = actor;
	}

	DashboardDtos.MyDashboardResponse myDashboard() {
		PersonalTaskSummary.Summary s = tasks.summaryFor(actor.currentActorId(), UPCOMING_LIMIT);
		DashboardDtos.MyTasks myTasks = new DashboardDtos.MyTasks(s.active(), s.overdue(),
				s.upcoming().stream().map(DashboardService::upcoming).toList());
		return new DashboardDtos.MyDashboardResponse(legacy(s), myTasks);
	}

	private static DashboardDtos.UpcomingTask upcoming(PersonalTaskSummary.TaskItem i) {
		return new DashboardDtos.UpcomingTask(i.taskId(), i.parentId(), i.spaceId(), i.title(), i.statusId(),
				i.statusName(), i.statusCategory() == null ? null : i.statusCategory().name(), i.deadline(),
				i.overdue());
	}

	/** The deprecated resolution-shaped section, filled from the same task data until the frontend switches. */
	private static DashboardDtos.MyResolutions legacy(PersonalTaskSummary.Summary s) {
		List<DashboardDtos.UpcomingResolution> upcoming = s.upcoming().stream()
				.map(i -> new DashboardDtos.UpcomingResolution(i.taskId(), i.taskId(), i.title(), i.deadline(),
						legacyState(i.statusCategory()), i.overdue()))
				.toList();
		return new DashboardDtos.MyResolutions(s.active(), s.overdue(), 0, upcoming);
	}

	/** Active tasks are in a TODO or IN_PROGRESS category; they map to the legacy NEW and IN_PROGRESS states. */
	static String legacyState(StatusCategory category) {
		return category == StatusCategory.IN_PROGRESS ? "IN_PROGRESS" : "NEW";
	}

}
