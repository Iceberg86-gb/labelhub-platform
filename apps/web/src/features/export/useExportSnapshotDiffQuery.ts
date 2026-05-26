import { useQuery } from '@tanstack/react-query';
import type { ExportSnapshotDiff } from '../../entities/export/exportTypes';
import { apiClient } from '../../shared/api/client';

type UseExportSnapshotDiffOptions = {
  enabled?: boolean;
};

export function useExportSnapshotDiffQuery(
  baseSnapshotId: number | null,
  compareSnapshotId: number | null,
  options: UseExportSnapshotDiffOptions = {},
) {
  return useQuery<ExportSnapshotDiff>({
    queryKey: ['export-diff', baseSnapshotId, compareSnapshotId],
    queryFn: async () => {
      if (!baseSnapshotId || !compareSnapshotId) {
        throw new Error('Both snapshot IDs required');
      }
      const { data, error } = await apiClient.GET('/exports/snapshots/{snapshotId}/diff', {
        params: { path: { snapshotId: baseSnapshotId }, query: { compareWith: compareSnapshotId } },
      });
      if (error || !data) {
        throw new Error('对比加载失败');
      }
      return data;
    },
    enabled: (options.enabled ?? true) && Boolean(baseSnapshotId) && Boolean(compareSnapshotId),
    staleTime: 60_000,
  });
}
