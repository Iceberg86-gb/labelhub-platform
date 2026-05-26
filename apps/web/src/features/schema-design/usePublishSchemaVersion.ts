import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { SchemaDocument, SchemaVersion } from '../../entities/schema/schemaTypes';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

type ApiFieldError = components['schemas']['ApiFieldError'];

export type PublishSchemaInput = {
  schemaId: number;
  schemaJson: SchemaDocument;
};

export class PublishSchemaFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly fieldErrors: ApiFieldError[] | undefined,
    message: string,
  ) {
    super(message);
  }
}

export function usePublishSchemaVersion() {
  const queryClient = useQueryClient();

  return useMutation<SchemaVersion, PublishSchemaFailure, PublishSchemaInput>({
    mutationFn: async ({ schemaId, schemaJson }) => {
      const { data, error, response } = await apiClient.POST('/schemas/{schemaId}/versions', {
        params: {
          path: { schemaId },
        },
        body: { schemaJson },
      });

      if (response.status === 400 && error?.fieldErrors) {
        throw new PublishSchemaFailure(
          400,
          error.code,
          error.fieldErrors,
          error.message ?? 'Schema 校验失败',
        );
      }

      if (response.status === 409 && error?.code === 'DUPLICATE_SCHEMA_VERSION_CONTENT') {
        throw new PublishSchemaFailure(
          409,
          error.code,
          error.fieldErrors,
          '该 Schema 内容与当前版本相同，无法重复发布',
        );
      }

      if (response.status === 409) {
        throw new PublishSchemaFailure(409, error?.code, error?.fieldErrors, '资源冲突，请稍后重试');
      }

      if (error || !data) {
        throw new PublishSchemaFailure(
          response.status,
          error?.code,
          error?.fieldErrors,
          error?.message ?? '发布失败，请重试',
        );
      }

      return data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['schemas', variables.schemaId] });
      await queryClient.invalidateQueries({ queryKey: ['schemas'], exact: false });
    },
  });
}
