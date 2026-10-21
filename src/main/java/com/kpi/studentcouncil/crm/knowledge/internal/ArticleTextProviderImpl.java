package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.knowledge.ArticleText;
import com.kpi.studentcouncil.crm.knowledge.ArticleTextProvider;

@Component
class ArticleTextProviderImpl implements ArticleTextProvider {

	private final ArticleService service;

	ArticleTextProviderImpl(ArticleService service) {
		this.service = service;
	}

	@Override
	public Optional<ArticleText> findText(UUID articleId) {
		return service.findText(articleId);
	}

	@Override
	public Optional<ArticleText> findTextForIndexing(UUID articleId) {
		return service.findTextForIndexing(articleId);
	}

	@Override
	public List<UUID> findActiveIds() {
		return service.findActiveIds();
	}

}
