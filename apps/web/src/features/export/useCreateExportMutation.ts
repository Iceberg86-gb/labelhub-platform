import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ExportFieldMapping, ExportJob, TrainingExportFormat, TrainingExportProfile } from '../../entities/export/exportTypes';
import { apiClient } from '../../shared/api/client';

export class CreateExportFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

type CreateExportVariables = {
  taskId: number;
  fieldMapping?: ExportFieldMapping;
  trainingFormat?: TrainingExportFormat;
  trainingProfile?: TrainingExportProfile;
  /** ADR-020: also export submissions admitted by the auto-pass policy (no human verdict). Opt-in only. */
  includePolicyAccepted?: boolean;
};

export function useCreateExportMutation() {
  const queryClient = useQueryClient();

  return useMutation<ExportJob, CreateExportFailure, CreateExportVariables>({
    mutationFn: async ({ taskId, fieldMapping, trainingFormat, trainingProfile, includePolicyAccepted }) => {
      const { data, error, response } = await apiClient.POST('/tasks/{taskId}/exports', {
        params: { path: { taskId } },
        body: {
          mode: exportModeFor(includePolicyAccepted),
          fieldMapping,
          trainingFormat: trainingFormat ?? 'flat_table',
          trainingProfile,
        },
      });
      if (error || !data) {
        const body = error as { code?: string; message?: string } | undefined;
        throw new CreateExportFailure(
          response.status,
          body?.code,
          mapExportErrorMessage(response.status, body?.code, body?.message),
        );
      }
      return data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['tasks', variables.taskId, 'exports'] });
    },
  });
}

/** Default is human-approved only; policy-admitted data is never exported unless the owner opts in. */
export function exportModeFor(includePolicyAccepted: boolean | undefined): 'approved_only' | 'approved_with_policy' {
  return includePolicyAccepted ? 'approved_with_policy' : 'approved_only';
}

function mapExportErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (status === 500 && code === 'EXPORT_FAILED') return '导出失败,请稍后重试';
  if (status === 404) return '任务不存在或无权访问';
  if (status === 403) return '没有导出权限';
  return message ?? '导出失败,请稍后重试';
}
