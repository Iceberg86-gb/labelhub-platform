import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { schemaListQueryKey } from './useSchemasQuery';

export type SchemaImportRequest = components['schemas']['SchemaImportRequest'];
export type SchemaImportResult = components['schemas']['SchemaImportResult'];
type ApiFieldError = components['schemas']['ApiFieldError'];

export type SchemaImportFailure = {
  message: string;
  fieldErrors?: ApiFieldError[];
};

export function useImportSchemaTemplateMutation() {
  const queryClient = useQueryClient();

  return useMutation<SchemaImportResult, SchemaImportFailure, SchemaImportRequest>({
    mutationFn: async (body) => {
      const { data, error } = await apiClient.POST('/schemas/import', { body });

      if (error || !data) {
        throw {
          message: error?.message ?? '模板导入失败。',
          fieldErrors: error?.fieldErrors,
        } satisfies SchemaImportFailure;
      }

      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: schemaListQueryKey(), exact: false });
    },
  });
}
