import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { LabelSchema } from '../../entities/schema/schemaTypes';
import type { components } from '../../shared/api/generated/schema';

export type PagedSchemas = components['schemas']['PagedSchemas'];

export type SchemaListParams = {
  page: number;
  size: number;
  q?: string;
};

export const schemaListQueryKey = (params?: Partial<SchemaListParams>) =>
  params ? ['schemas', 'list', params] : ['schemas', 'list'];

export function useSchemasQuery(params: SchemaListParams) {
  return useQuery<PagedSchemas>({
    queryKey: schemaListQueryKey(params),
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/schemas', {
        params: {
          query: {
            page: params.page,
            size: params.size,
            q: params.q,
          },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Schema 列表加载失败。');
      }

      return data;
    },
  });
}

export type { LabelSchema };

