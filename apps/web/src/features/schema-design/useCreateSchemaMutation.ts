import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { taskDetailQueryKey } from '../task/task-detail/useTaskDetailQuery';
import { taskListQueryKey } from '../task/list-tasks/useTasksQuery';
import { schemaListQueryKey } from './useSchemasQuery';

export type CreateSchemaRequest = components['schemas']['CreateSchemaRequest'];
export type LabelSchema = components['schemas']['LabelSchema'];
type ApiFieldError = components['schemas']['ApiFieldError'];

export type CreateSchemaFailure = {
  message: string;
  fieldErrors?: ApiFieldError[];
};

export async function createSchema(body: CreateSchemaRequest): Promise<LabelSchema> {
  const { data, error } = await apiClient.POST('/schemas', { body });

  if (error || !data) {
    throw {
      message: error?.message ?? 'Schema 创建失败。',
      fieldErrors: error?.fieldErrors,
    } satisfies CreateSchemaFailure;
  }

  return data;
}

export function useCreateSchemaMutation() {
  const queryClient = useQueryClient();

  return useMutation<LabelSchema, CreateSchemaFailure, CreateSchemaRequest>({
    mutationFn: createSchema,
    onSuccess: async (schema) => {
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: schemaListQueryKey(), exact: false }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
      ];
      if (schema.taskId != null) {
        invalidations.push(queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(schema.taskId) }));
      }
      await Promise.all(invalidations);
    },
  });
}
