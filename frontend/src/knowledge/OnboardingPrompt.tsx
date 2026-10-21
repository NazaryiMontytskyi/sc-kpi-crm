import { Alert, Button, Group, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { KB_VIEW } from './knowledgeApi';
import { useDismissOnboardingPrompt, useMyOnboarding, useOnboardingPrompt } from './useOnboarding';

/**
 * One-time invitation for a newcomer to open "For newcomers" on their first visit. It is shown until the user dismisses
 * or opens it, and only when there is something to read; it never blocks navigation (unlike the forced password change).
 */
export function OnboardingPrompt() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const canView = usePermission(KB_VIEW);
  const prompt = useOnboardingPrompt(canView);
  const lists = useMyOnboarding(canView && prompt.data === true);
  const dismiss = useDismissOnboardingPrompt();
  const hasContent = (lists.data ?? []).some((l) => l.items.length > 0);
  if (!canView || prompt.data !== true || !hasContent) return null;
  return (
    <Alert color="studrada" variant="light" title={t('knowledge.onboarding.prompt.title')} data-testid="onboarding-prompt">
      <Text size="sm" mb="sm">
        {t('knowledge.onboarding.prompt.text')}
      </Text>
      <Group gap="xs">
        <Button
          mih={44}
          loading={dismiss.isPending}
          onClick={() => dismiss.mutate(undefined, { onSuccess: () => navigate('/knowledge/onboarding') })}
        >
          {t('knowledge.onboarding.prompt.open')}
        </Button>
        <Button variant="default" mih={44} loading={dismiss.isPending} onClick={() => dismiss.mutate()}>
          {t('knowledge.onboarding.prompt.later')}
        </Button>
      </Group>
    </Alert>
  );
}
