import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import { schemaListQueryKey } from './useSchemasQuery';

export type SchemaArchiveFailure = {
  message: string;
};

export function useArchiveSchemaTemplateMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, SchemaArchiveFailure, number>({
    mutationFn: async (schemaId) => {
      const { error } = await apiClient.DELETE('/schemas/{schemaId}', {
        params: {
          path: { schemaId },
        },
      });

      if (error) {
        throw { message: error.message ?? '模板删除失败。' } satisfies SchemaArchiveFailure;
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: schemaListQueryKey(), exact: false });
    },
  });
}
