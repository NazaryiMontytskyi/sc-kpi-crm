import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { archiveFaculty, createFaculty, listFaculties, restoreFaculty, updateFaculty, type FacultyRequest } from './facultyApi';

export const FACULTIES_KEY = ['faculties'] as const;

export function useFaculties(includeArchived = false) {
  return useQuery({ queryKey: [...FACULTIES_KEY, { includeArchived }], queryFn: () => listFaculties(includeArchived) });
}

export function useFacultyMutations() {
  const queryClient = useQueryClient();
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: FACULTIES_KEY });
  return {
    create: useMutation({ mutationFn: (body: FacultyRequest) => createFaculty(body), onSuccess }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: string; body: FacultyRequest }) => updateFaculty(id, body),
      onSuccess,
    }),
    archive: useMutation({ mutationFn: (id: string) => archiveFaculty(id), onSuccess }),
    restore: useMutation({ mutationFn: (id: string) => restoreFaculty(id), onSuccess }),
  };
}
