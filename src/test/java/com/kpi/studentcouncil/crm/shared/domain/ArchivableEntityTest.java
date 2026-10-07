package com.kpi.studentcouncil.crm.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.ConflictException;

class ArchivableEntityTest {

	static class Dummy extends ArchivableEntity {
	}

	private final UUID actor = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-01-01T10:00:00Z");

	@Test
	void archiveSetsArchivedAtAndBy() {
		Dummy entity = new Dummy();

		entity.archive(actor, now);

		assertThat(entity.isArchived()).isTrue();
		assertThat(entity.getArchivedAt()).isEqualTo(now);
		assertThat(entity.getArchivedBy()).isEqualTo(actor);
	}

	@Test
	void restoreClearsArchiveFields() {
		Dummy entity = new Dummy();
		entity.archive(actor, now);

		entity.restore();

		assertThat(entity.isArchived()).isFalse();
		assertThat(entity.getArchivedAt()).isNull();
		assertThat(entity.getArchivedBy()).isNull();
	}

	@Test
	void archivingTwiceFails() {
		Dummy entity = new Dummy();
		entity.archive(actor, now);

		assertThatThrownBy(() -> entity.archive(actor, now)).isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("ALREADY_ARCHIVED");
	}

	@Test
	void restoringNonArchivedFails() {
		assertThatThrownBy(() -> new Dummy().restore()).isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("NOT_ARCHIVED");
	}

}
