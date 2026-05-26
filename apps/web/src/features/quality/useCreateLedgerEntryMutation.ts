import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { QualityLedgerEntry, QualityLedgerEntryType, ReviewerOverallVerdictPayload } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export class CreateLedgerEntryFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

export type CreateLedgerEntryVariables = {
  submissionId: number;
  entryType: QualityLedgerEntryType;
  payload: ReviewerOverallVerdictPayload;
};

export function useCreateLedgerEntryMutation() {
  const queryClient = useQueryClient();

  return useMutation<QualityLedgerEntry, CreateLedgerEntryFailure, CreateLedgerEntryVariables>({
    mutationFn: async ({ submissionId, entryType, payload }) => {
      const { data, error, response } = await apiClient.POST('/submissions/{submissionId}/ledger-entries', {
        params: { path: { submissionId } },
        body: { entryType, payload },
      });
      if (error || !data) {
        const body = error as { code?: string; message?: string } | undefined;
        throw new CreateLedgerEntryFailure(
          response.status,
          body?.code,
          mapLedgerErrorMessage(response.status, body?.code, body?.message),
        );
      }
      return data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['submissions', variables.submissionId, 'verdict'] }),
        queryClient.invalidateQueries({ queryKey: ['submissions', variables.submissionId, 'ledger-entries'] }),
        queryClient.invalidateQueries({ queryKey: ['reviewer-queue'] }),
      ]);
    },
  });
}

function mapLedgerErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (status === 409 && code === 'SELF_REVIEW_NOT_ALLOWED') return '不能审核自己提交的内容';
  if (status === 400 && code === 'LEDGER_ENTRY_PAYLOAD_INVALID') return message ?? '审核内容格式不正确';
  if (status === 400 && code === 'LEDGER_ENTRY_TYPE_NOT_SUPPORTED') return 'M4 仅支持整体审核';
  if (status === 404) return 'Submission 不存在或无权访问';
  if (status === 403) return '没有审核权限';
  return message ?? '提交审核失败,请稍后重试';
}
