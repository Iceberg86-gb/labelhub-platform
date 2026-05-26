import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { SchemaVersion } from '../../entities/schema/schemaTypes';

export const schemaVersionsQueryKey = (schemaId: number) => ['schemas', schemaId, 'versions'] as const;

export function useSchemaVersionsQuery(schemaId: number) {
  return useQuery<SchemaVersion[]>({
    queryKey: schemaVersionsQueryKey(schemaId),
    enabled: schemaId > 0,
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/schemas/{schemaId}/versions', {
        params: {
          path: { schemaId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Schema 版本列表加载失败。');
      }

      return data;
    },
  });
}

