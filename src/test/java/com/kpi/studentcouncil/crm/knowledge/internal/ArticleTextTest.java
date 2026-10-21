package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.knowledge.ArticleText;

import org.springframework.context.ApplicationEventPublisher;

/** Text extraction for search must follow the visibility of restricted articles. */
class ArticleTextTest {

	private final ArticleRepository repository = mock(ArticleRepository.class);
	private final ArticleViewer viewer = mock(ArticleViewer.class);
	private final ArticleService service = new ArticleService(repository, viewer, mock(FileService.class),
			mock(AuditPublisher.class), mock(ActorProvider.class), mock(ApplicationEventPublisher.class),
			Clock.systemUTC());

	private Article article(boolean restricted) {
		Map<String, Object> text = Map.of("type", "text", "text", "Budget rules");
		Map<String, Object> paragraph = Map.of("type", "paragraph", "content", List.of(text));
		Map<String, Object> doc = Map.of("type", "doc", "content", List.of(paragraph));
		return new Article("Finance", doc, null, restricted, Set.of("money"), List.of(), Set.of());
	}

	@Test
	void openArticleTextIsAvailableToEveryone() {
		Article open = article(false);
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(open));
		when(viewer.canSee(any())).thenAnswer(i -> !((Article) i.getArgument(0)).isRestricted());

		ArticleText text = service.findText(id).orElseThrow();
		assertThat(text.title()).isEqualTo("Finance");
		assertThat(text.text()).isEqualTo("Budget rules");
		assertThat(text.tags()).containsExactly("money");
		assertThat(text.restricted()).isFalse();
	}

	@Test
	void restrictedArticleTextIsWithheldFromActorsWithoutThePermission() {
		Article secret = article(true);
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(secret));
		when(viewer.canSee(any())).thenReturn(false);
		assertThat(service.findText(id)).isEmpty();

		when(viewer.canSee(any())).thenReturn(true);
		assertThat(service.findText(id)).get().extracting(ArticleText::restricted).isEqualTo(true);
	}

	@Test
	void unknownArticleHasNoText() {
		when(repository.findById(any())).thenReturn(Optional.empty());
		assertThat(service.findText(UUID.randomUUID())).isEmpty();
	}

}
