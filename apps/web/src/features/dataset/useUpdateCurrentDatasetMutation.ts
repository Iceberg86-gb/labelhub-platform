import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Dataset } from '../../entities/dataset/datasetTypes';
import type { Task } from '../task/list-tasks/useTasksQuery';
import { taskListQueryKey } from '../task/list-tasks/useTasksQuery';
import { taskDetailQueryKey } from '../task/task-detail/useTaskDetailQuery';
import { apiClient } from '../../shared/api/client';

export type UpdateCurrentDatasetVariables = {
  taskId: number;
  datasetId: Dataset['id'];
};

export class UpdateCurrentDatasetFailure extends Error {
  constructor(public readonly status: number, public readonly code: string | undefined, public readonly userMessage: string) {
    super(userMessage);
  }
}

export function useUpdateCurrentDatasetMutation() {
  const queryClient = useQueryClient();

  return useMutation<Task, UpdateCurrentDatasetFailure, UpdateCurrentDatasetVariables>({
    mutationFn: async ({ taskId, datasetId }) => {
      const { data, error, response } = await apiClient.PATCH('/tasks/{taskId}/current-dataset', {
        params: { path: { taskId } },
        body: { datasetId },
      });

      if (error || !data) {
        throw new UpdateCurrentDatasetFailure(
          response.status,
          error?.code,
          mapUpdateErrorMessage(response.status, error?.code, error?.message),
        );
      }

      return data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(variables.taskId) }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
      ]);
    },
  });
}

function mapUpdateErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (status === 409 && code === 'TASK_PUBLISHED_LOCK') return '任务已发布,无法修改数据集';
  if (status === 400 && code === 'INVALID_DATASET_FOR_TASK') return '数据集与任务不匹配';
  if (status === 404) return '任务不存在或无权访问';
  return message ?? '设置当前数据集失败,请稍后重试';
}
