import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { SchemaVersion } from '../../entities/schema/schemaTypes';

export const schemaVersionQueryKey = (schemaId: number, versionId: number | null) =>
  ['schemas', schemaId, 'versions', versionId] as const;

export function useSchemaVersionQuery(schemaId: number, versionId: number | null) {
  return useQuery<SchemaVersion>({
    queryKey: schemaVersionQueryKey(schemaId, versionId),
    enabled: schemaId > 0 && Boolean(versionId),
    staleTime: 30_000,
    queryFn: async () => {
      if (!versionId) {
        throw new Error('Schema version id is required.');
      }

      const { data, error } = await apiClient.GET('/schemas/{schemaId}/versions/{versionId}', {
        params: {
          path: { schemaId, versionId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Schema 版本详情加载失败。');
      }

      return data;
    },
  });
}

