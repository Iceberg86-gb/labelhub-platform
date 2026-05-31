import { useQuery } from '@tanstack/react-query';
import type { PagedExportSnapshots } from '../../entities/export/exportTypes';
import { apiClient } from '../../shared/api/client';

type UseTaskExportsOptions = {
  page?: number;
  size?: number;
  archived?: boolean;
  enabled?: boolean;
};

export function useTaskExportsQuery(taskId: number, options: UseTaskExportsOptions = {}) {
  const page = options.page ?? 1;
  const size = options.size ?? 20;
  const archived = options.archived ?? false;

  return useQuery<PagedExportSnapshots>({
    queryKey: ['tasks', taskId, 'exports', page, size, archived],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/exports', {
        params: { path: { taskId }, query: { page, size, archived } },
      });
      if (error || !data) {
        throw new Error('导出列表加载失败');
      }
      return data;
    },
    enabled: options.enabled ?? true,
  });
}
