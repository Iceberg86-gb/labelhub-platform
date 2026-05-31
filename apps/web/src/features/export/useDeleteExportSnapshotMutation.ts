import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';

type DeleteExportSnapshotVariables = {
  taskId: number;
  snapshotId: number;
};

export function useDeleteExportSnapshotMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, DeleteExportSnapshotVariables>({
    mutationFn: async ({ snapshotId }) => {
      const { error, response } = await apiClient.DELETE('/exports/snapshots/{snapshotId}', {
        params: { path: { snapshotId } },
      });
      if (error) {
        throw new Error(mapDeleteExportSnapshotError(response.status));
      }
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['tasks', variables.taskId, 'exports'] });
    },
  });
}

function mapDeleteExportSnapshotError(status: number): string {
  if (status === 403) return '无权限删除此导出快照';
  if (status === 404) return '导出快照不存在或已被删除';
  return '删除失败,请稍后重试';
}
