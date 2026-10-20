package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface TaskLinkRepository extends Repository<TaskLink, TaskLinkId> {

	TaskLink save(TaskLink link);

	void delete(TaskLink link);

	@Query("select l from TaskLink l where l.id.low in :ids or l.id.high in :ids")
	List<TaskLink> findAllTouching(@Param("ids") Collection<UUID> ids);

}
