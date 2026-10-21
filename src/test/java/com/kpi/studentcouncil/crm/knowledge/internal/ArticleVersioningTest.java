package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.knowledge.ArticleEvent;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/** Business rules of versioning: one snapshot per save, optimistic locking, restore appends. */
class ArticleVersioningTest {

	private final ArticleRepository articles = mock(ArticleRepository.class);
	private final ArticleVersionRepository versions = mock(ArticleVersionRepository.class);
	private final ArticleViewer viewer = mock(ArticleViewer.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private ArticleService service;
	private Article article;
	private final UUID id = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new ArticleService(articles, versions, viewer, mock(FileService.class), mock(AuditPublisher.class),
				mock(ActorProvider.class), events, Clock.systemUTC());
		article = new Article("Title", doc("v1"), null, false, Set.of(), List.of(), Set.of());
		when(viewer.canSee(any())).thenReturn(true);
		when(articles.lockById(id)).thenReturn(Optional.of(article));
		when(articles.save(any(Article.class))).thenAnswer(i -> i.getArgument(0));
	}

	private static Map<String, Object> doc(String text) {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("type", "doc");
		d.put("content", List.of(Map.of("type", "paragraph", "content",
				List.of(Map.of("type", "text", "text", text)))));
		return d;
	}

	@Test
	void saveSnapshotsTheNewVersionNumber() {
		service.update(id, 1, "Title 2", doc("v2"), false, List.of(), List.of());

		ArgumentCaptor<ArticleVersion> saved = ArgumentCaptor.forClass(ArticleVersion.class);
		verify(versions).save(saved.capture());
		assertThat(saved.getValue().getVersionNumber()).isEqualTo(2);
		assertThat(saved.getValue().getTitle()).isEqualTo("Title 2");
		assertThat(article.getVersion()).isEqualTo(2);
		verify(events).publishEvent(any(ArticleEvent.class));
	}

	@Test
	void staleVersionIsAConflictAndSavesNothing() {
		assertThatThrownBy(() -> service.update(id, 5, "T", doc("x"), false, List.of(), List.of()))
				.isInstanceOf(ConflictException.class).hasMessageContaining("ARTICLE_VERSION_CONFLICT");
		verify(versions, never()).save(any());
		assertThat(article.getVersion()).isEqualTo(1);
	}

	@Test
	void restoreCopiesTitleAndContentIntoANewVersion() {
		ArticleVersion old = new ArticleVersion(id, 1, "Old title", doc("old"));
		when(versions.findByArticleIdAndVersionNumber(id, 1)).thenReturn(Optional.of(old));
		article.edit("Newer", doc("new"), false, Set.of(), List.of(), Set.of());

		service.restoreVersion(id, 1, 2);

		ArgumentCaptor<ArticleVersion> saved = ArgumentCaptor.forClass(ArticleVersion.class);
		verify(versions).save(saved.capture());
		assertThat(saved.getValue().getVersionNumber()).isEqualTo(3);
		assertThat(saved.getValue().getContent()).isEqualTo(old.getContent());
		assertThat(article.getTitle()).isEqualTo("Old title");
		assertThat(article.getVersion()).isEqualTo(3);
	}

	@Test
	void restoreOfUnknownVersionIsNotFound() {
		when(versions.findByArticleIdAndVersionNumber(id, 9)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.restoreVersion(id, 9, null)).isInstanceOf(NotFoundException.class);
		verify(versions, never()).save(any());
	}

}
