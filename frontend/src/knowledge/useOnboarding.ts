import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  archiveOnboardingList,
  createOnboardingList,
  dismissOnboardingPrompt,
  getManagedOnboarding,
  getMyOnboarding,
  getOnboardingPrompt,
  getReadMark,
  restoreOnboardingList,
  setReadMark,
  updateOnboardingList,
} from './onboardingApi';

const ONBOARDING_KEY = ['knowledge', 'onboarding'] as const;

export const useMyOnboarding = (enabled = true) =>
  useQuery({ queryKey: [...ONBOARDING_KEY, 'mine'], queryFn: getMyOnboarding, enabled });
export const useManagedOnboarding = (includeArchived: boolean) =>
  useQuery({ queryKey: [...ONBOARDING_KEY, 'managed', includeArchived], queryFn: () => getManagedOnboarding(includeArchived) });

export function useOnboardingMutations() {
  const qc = useQueryClient();
  const onSuccess = () => qc.invalidateQueries({ queryKey: ONBOARDING_KEY });
  return {
    create: useMutation({
      mutationFn: (v: { title: string; orgUnitId: string | null; articleIds: string[] }) =>
        createOnboardingList(v.title, v.orgUnitId, v.articleIds),
      onSuccess,
    }),
    update: useMutation({
      mutationFn: (v: { id: string; title: string; articleIds: string[] }) => updateOnboardingList(v.id, v.title, v.articleIds),
      onSuccess,
    }),
    archive: useMutation({ mutationFn: (id: string) => archiveOnboardingList(id), onSuccess }),
    restore: useMutation({ mutationFn: (id: string) => restoreOnboardingList(id), onSuccess }),
  };
}

export const useReadMark = (articleId: string | undefined) =>
  useQuery({ queryKey: [...ONBOARDING_KEY, 'readMark', articleId], queryFn: () => getReadMark(articleId ?? ''), enabled: !!articleId });

export function useSetReadMark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { articleId: string; read: boolean }) => setReadMark(v.articleId, v.read),
    onSuccess: (_read, v) => {
      qc.setQueryData([...ONBOARDING_KEY, 'readMark', v.articleId], v.read);
      return qc.invalidateQueries({ queryKey: [...ONBOARDING_KEY, 'mine'] });
    },
  });
}

export const useOnboardingPrompt = (enabled: boolean) =>
  useQuery({ queryKey: [...ONBOARDING_KEY, 'prompt'], queryFn: getOnboardingPrompt, enabled });

export function useDismissOnboardingPrompt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: dismissOnboardingPrompt,
    onSuccess: () => qc.setQueryData([...ONBOARDING_KEY, 'prompt'], false),
  });
}
