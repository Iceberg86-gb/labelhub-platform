import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';
import { taskListQueryKey } from '../list-tasks/useTasksQuery';

export type CreateTaskRequest = components['schemas']['CreateTaskRequest'];
type ApiFieldError = components['schemas']['ApiFieldError'];

export type CreateTaskFailure = {
  message: string;
  fieldErrors?: ApiFieldError[];
};

export function useCreateTask() {
  const queryClient = useQueryClient();

  return useMutation<components['schemas']['Task'], CreateTaskFailure, CreateTaskRequest>({
    mutationFn: async (body) => {
      const { data, error } = await apiClient.POST('/tasks', {
        body,
      });

      if (error || !data) {
        throw {
          message: error?.message ?? '任务创建失败。',
          fieldErrors: error?.fieldErrors,
        } satisfies CreateTaskFailure;
      }

      await queryClient.invalidateQueries({ queryKey: taskListQueryKey() });
      return data;
    },
  });
}
