import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ExportSnapshot } from '../../entities/export/exportTypes';
import { apiClient } from '../../shared/api/client';

type ArchiveExportSnapshotVariables = {
  taskId: number;
  snapshotId: number;
};

export function useArchiveExportSnapshotMutation() {
  const queryClient = useQueryClient();

  return useMutation<ExportSnapshot, Error, ArchiveExportSnapshotVariables>({
    mutationFn: async ({ snapshotId }) => {
      const { data, error, response } = await apiClient.POST('/exports/snapshots/{snapshotId}/archive', {
        params: { path: { snapshotId } },
      });
      if (error || !data) {
        throw new Error(mapArchiveExportSnapshotError(response.status));
      }
      return data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['tasks', variables.taskId, 'exports'] });
    },
  });
}

function mapArchiveExportSnapshotError(status: number): string {
  if (status === 403) return '无权限归档此导出快照';
  if (status === 404) return '导出快照不存在或已归档';
  return '归档失败,请稍后重试';
}
