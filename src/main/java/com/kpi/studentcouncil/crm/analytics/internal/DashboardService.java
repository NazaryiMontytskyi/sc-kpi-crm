package com.kpi.studentcouncil.crm.analytics.internal;

import org.springframework.stereotype.Service;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.tasks.PersonalTaskSummary;

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
		return new DashboardDtos.MyDashboardResponse(myTasks);
	}

	private static DashboardDtos.UpcomingTask upcoming(PersonalTaskSummary.TaskItem i) {
		return new DashboardDtos.UpcomingTask(i.taskId(), i.parentId(), i.spaceId(), i.title(), i.statusId(),
				i.statusName(), i.statusCategory() == null ? null : i.statusCategory().name(), i.deadline(),
				i.overdue());
	}

}
