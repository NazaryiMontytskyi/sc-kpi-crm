package com.kpi.studentcouncil.crm.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.kpi.studentcouncil.crm.TestcontainersConfiguration;
import com.kpi.studentcouncil.crm.shared.config.JpaAuditingConfig;
import com.kpi.studentcouncil.crm.shared.domain.ArchivableSpecs;
import com.kpi.studentcouncil.crmtest.shared.KernelTestConfig;
import com.kpi.studentcouncil.crmtest.shared.TestNote;
import com.kpi.studentcouncil.crmtest.shared.TestNoteRepository;

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false" })
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, KernelTestConfig.class })
class SoftDeleteAuditingIntegrationTests {

	private static final UUID ACTOR = UUID.randomUUID();

	@Autowired
	TestNoteRepository repository;

	@Test
	void auditFieldsArePopulatedAutomaticallyInUtc() throws Exception {
		Instant before = Instant.now().minusSeconds(1);

		TestNote saved = repository.save(new TestNote("first"));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(saved.getCreatedBy()).isEqualTo(JpaAuditingConfig.SYSTEM_ACTOR);
		assertThat(saved.getUpdatedBy()).isEqualTo(JpaAuditingConfig.SYSTEM_ACTOR);

		Thread.sleep(20);
		TestNote loaded = repository.findById(saved.getId()).orElseThrow();
		loaded.setTitle("changed");
		TestNote updated = repository.save(loaded);

		assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
		assertThat(repository.findById(saved.getId()).orElseThrow().getCreatedAt())
				.isCloseTo(saved.getCreatedAt(), org.assertj.core.api.Assertions.within(Duration.ofMillis(1)));
	}

	@Test
	void archivedRowsAreExcludedFromDefaultQueriesAndRestorable() {
		TestNote note = repository.save(new TestNote("to-archive"));
		UUID id = note.getId();

		TestNote loaded = repository.findById(id).orElseThrow();
		loaded.archive(ACTOR, Instant.now());
		repository.save(loaded);

		assertThat(repository.findByIdAndArchivedAtIsNull(id)).isEmpty();
		assertThat(repository.findByArchivedAtIsNull(PageRequest.of(0, 1000)).getContent())
				.extracting(TestNote::getId).doesNotContain(id);
		assertThat(repository.findActive(null, PageRequest.of(0, 1000)).getContent())
				.extracting(TestNote::getId).doesNotContain(id);
		assertThat(repository.findAll(ArchivableSpecs.<TestNote>archived(), PageRequest.of(0, 1000)).getContent())
				.extracting(TestNote::getId).contains(id);

		TestNote archived = repository.findById(id).orElseThrow();
		assertThat(archived.getArchivedAt()).isNotNull();
		assertThat(archived.getArchivedBy()).isEqualTo(ACTOR);

		archived.restore();
		repository.save(archived);

		assertThat(repository.findByIdAndArchivedAtIsNull(id)).isPresent();
		TestNote restored = repository.findById(id).orElseThrow();
		assertThat(restored.getArchivedAt()).isNull();
		assertThat(restored.getArchivedBy()).isNull();
	}

	@Test
	void baseRepositoryExposesNoDeleteMethods() {
		assertThat(com.kpi.studentcouncil.crm.shared.domain.ArchivableRepository.class.getMethods())
				.extracting(java.lang.reflect.Method::getName)
				.noneMatch(name -> name.toLowerCase().contains("delete"));
	}

}
