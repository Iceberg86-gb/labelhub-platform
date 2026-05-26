import { useQuery } from '@tanstack/react-query';
import type { PagedQualityLedgerEntries } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export type LedgerEntriesQueryOptions = {
  page?: number;
  size?: number;
  enabled?: boolean;
};

export const ledgerEntriesQueryKey = (submissionId: number, page = 1, size = 50) =>
  ['submissions', submissionId, 'ledger-entries', page, size] as const;

export function useLedgerEntriesQuery(submissionId: number, options: LedgerEntriesQueryOptions = {}) {
  const page = options.page ?? 1;
  const size = options.size ?? 50;

  return useQuery<PagedQualityLedgerEntries | null>({
    queryKey: ledgerEntriesQueryKey(submissionId, page, size),
    enabled: (options.enabled ?? true) && submissionId > 0,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/submissions/{submissionId}/ledger-entries', {
        params: { path: { submissionId }, query: { page, size } },
      });
      if (response.status === 404) return null;
      if (error || !data) throw new Error(error?.message ?? '审核历史加载失败。');
      return data;
    },
  });
}
