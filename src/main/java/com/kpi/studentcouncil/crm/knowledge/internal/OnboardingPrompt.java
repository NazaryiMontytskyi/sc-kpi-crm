package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.BaseEntity;

/** Marks that the user has seen (dismissed) the one-time newcomer prompt. */
@Entity
@Table(name = "kb_onboarding_prompt")
class OnboardingPrompt extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	protected OnboardingPrompt() {
	}

	OnboardingPrompt(UUID userId) {
		this.userId = userId;
	}

}
