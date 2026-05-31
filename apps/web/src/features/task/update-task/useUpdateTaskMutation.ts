import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';
import { taskListQueryKey, type Task } from '../list-tasks/useTasksQuery';
import { taskDetailQueryKey } from '../task-detail/useTaskDetailQuery';

export type UpdateTaskRequest = components['schemas']['UpdateTaskRequest'];

export type UpdateTaskVariables = {
  taskId: number;
  body: UpdateTaskRequest;
};

export class UpdateTaskFailure extends Error {
  constructor(public readonly status: number, public readonly code: string | undefined, public readonly userMessage: string) {
    super(userMessage);
  }
}

export function useUpdateTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation<Task, UpdateTaskFailure, UpdateTaskVariables>({
    mutationFn: async ({ taskId, body }) => {
      const { data, error, response } = await apiClient.PATCH('/tasks/{taskId}', {
        params: { path: { taskId } },
        body,
      });

      if (error || !data) {
        throw new UpdateTaskFailure(response.status, error?.code, mapUpdateTaskError(response.status, error?.code, error?.message));
      }

      return data;
    },
    onSuccess: async (_task, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(variables.taskId) }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
      ]);
    },
  });
}

function mapUpdateTaskError(status: number, code: string | undefined, message: string | undefined) {
  if (status === 409 && code === 'TASK_EDITING_LOCKED') return '仅草稿或已暂停任务可编辑基础信息';
  if (status === 404) return '任务不存在或无权访问';
  return message ?? '任务更新失败,请稍后重试';
}
