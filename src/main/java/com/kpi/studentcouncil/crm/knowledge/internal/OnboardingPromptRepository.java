package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.UUID;

import org.springframework.data.repository.Repository;

interface OnboardingPromptRepository extends Repository<OnboardingPrompt, UUID> {

	OnboardingPrompt save(OnboardingPrompt prompt);

	boolean existsByUserId(UUID userId);

}
