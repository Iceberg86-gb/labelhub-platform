import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';
import { taskListQueryKey } from '../list-tasks/useTasksQuery';
import { taskDetailQueryKey } from '../task-detail/useTaskDetailQuery';
import { taskTransitionsQueryKey } from '../task-transitions/useTaskTransitionsQuery';

type TaskStatus = components['schemas']['TaskStatus'];
type Task = components['schemas']['Task'];
type ApiFieldError = components['schemas']['ApiFieldError'];

export type TransitionTaskInput = {
  taskId: number;
  toStatus: TaskStatus;
  reason: string;
};

export type TransitionTaskFailure = {
  status: number;
  message: string;
  fieldErrors?: ApiFieldError[];
};

export function useTransitionTask() {
  const queryClient = useQueryClient();

  return useMutation<Task, TransitionTaskFailure, TransitionTaskInput>({
    mutationFn: async ({ taskId, toStatus, reason }) => {
      const { data, error, response } = await apiClient.PATCH('/tasks/{taskId}/transition', {
        params: {
          path: { taskId },
        },
        body: {
          toStatus,
          reason,
        },
      });

      if (error || !data) {
        throw {
          status: response.status,
          message: error?.message ?? '状态迁移失败。',
          fieldErrors: error?.fieldErrors,
        } satisfies TransitionTaskFailure;
      }

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(taskId) }),
        queryClient.invalidateQueries({ queryKey: taskTransitionsQueryKey(taskId) }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
      ]);

      return data;
    },
  });
}
