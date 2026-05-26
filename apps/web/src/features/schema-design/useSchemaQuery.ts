import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { LabelSchema } from '../../entities/schema/schemaTypes';

export const schemaDetailQueryKey = (schemaId: number) => ['schemas', schemaId] as const;

export function useSchemaQuery(schemaId: number) {
  return useQuery<LabelSchema>({
    queryKey: schemaDetailQueryKey(schemaId),
    enabled: schemaId > 0,
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/schemas/{schemaId}', {
        params: {
          path: { schemaId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Schema 详情加载失败。');
      }

      return data;
    },
  });
}

