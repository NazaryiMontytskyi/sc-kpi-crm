package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.tasks.ActiveResolutionQuery;

/** Implements the public {@link ActiveResolutionQuery}; exposes no entities. */
@Service
class ActiveResolutionQueryService implements ActiveResolutionQuery {

	private final ResolutionRepository resolutions;

	ActiveResolutionQueryService(ResolutionRepository resolutions) {
		this.resolutions = resolutions;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ActiveResolution> findActiveDueOnOrBefore(LocalDate latestDueDate) {
		return resolutions.findOpenDueOnOrBefore(latestDueDate).stream()
				.map(r -> new ActiveResolution(r.getId(), r.getTaskId(), r.getAuthorId(), r.getExecutorId(),
						r.getCoExecutorIds(), r.getDueDate()))
				.toList();
	}

}
