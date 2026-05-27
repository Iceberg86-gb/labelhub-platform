import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import { taskListQueryKey } from '../list-tasks/useTasksQuery';

export type DeleteTaskInput = {
  taskId: number;
};

export function useDeleteTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, DeleteTaskInput>({
    mutationFn: async ({ taskId }) => {
      const { error, response } = await apiClient.DELETE('/tasks/{taskId}', {
        params: { path: { taskId } },
      });

      if (error) {
        throw new Error(mapDeleteTaskError(response.status));
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false });
    },
  });
}

function mapDeleteTaskError(status: number): string {
  if (status === 403) return '无权限删除此任务';
  if (status === 404) return '任务不存在或已被删除';
  return '删除失败,请稍后重试';
}
