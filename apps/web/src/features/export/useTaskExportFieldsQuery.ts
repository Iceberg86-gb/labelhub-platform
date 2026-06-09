import { useQuery } from '@tanstack/react-query';
import type { ExportFieldCatalog } from '../../entities/export/exportTypes';
import { apiClient } from '../../shared/api/client';

export function useTaskExportFieldsQuery(taskId: number, options: { enabled?: boolean } = {}) {
  return useQuery<ExportFieldCatalog>({
    queryKey: ['tasks', taskId, 'export-fields'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/export-fields', {
        params: { path: { taskId } },
      });
      if (error || !data) {
        throw new Error('导出字段目录加载失败');
      }
      return data;
    },
    enabled: options.enabled ?? true,
  });
}
