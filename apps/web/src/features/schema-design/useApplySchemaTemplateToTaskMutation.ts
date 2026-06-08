import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { taskDetailQueryKey } from '../task/task-detail/useTaskDetailQuery';
import { taskListQueryKey } from '../task/list-tasks/useTasksQuery';
import { schemaListQueryKey } from './useSchemasQuery';

export type ApplySchemaTemplateInput = {
  taskId: number;
  schemaId: number;
  versionId?: number;
};

export type ApplySchemaTemplateResult = components['schemas']['ApplySchemaTemplateResult'];

export type ApplySchemaTemplateFailure = {
  message: string;
};

export function useApplySchemaTemplateToTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation<ApplySchemaTemplateResult, ApplySchemaTemplateFailure, ApplySchemaTemplateInput>({
    mutationFn: async ({ taskId, schemaId, versionId }) => {
      const { data, error } = await apiClient.POST('/tasks/{taskId}/schema-from-template', {
        params: {
          path: { taskId },
        },
        body: { schemaId, versionId },
      });

      if (error || !data) {
        throw { message: error?.message ?? '模板绑定失败。' } satisfies ApplySchemaTemplateFailure;
      }

      return data;
    },
    onSuccess: async (_result, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(variables.taskId) }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
        queryClient.invalidateQueries({ queryKey: schemaListQueryKey(), exact: false }),
      ]);
    },
  });
}
