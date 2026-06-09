import { useQuery } from '@tanstack/react-query';
import type { PagedSeniorReviewCases } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export type SeniorReviewCasesQueryOptions = {
  page?: number;
  size?: number;
  enabled?: boolean;
};

export const seniorReviewCasesQueryKey = (options: SeniorReviewCasesQueryOptions = {}) =>
  ['senior-review-cases', options.page ?? 1, options.size ?? 20] as const;

export function useSeniorReviewCasesQuery(options: SeniorReviewCasesQueryOptions = {}) {
  const page = options.page ?? 1;
  const size = options.size ?? 20;

  return useQuery<PagedSeniorReviewCases>({
    queryKey: seniorReviewCasesQueryKey({ page, size }),
    enabled: options.enabled ?? true,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/senior-review/cases', {
        params: { query: { page, size } },
      });
      if (error || !data) {
        throw new Error(response.status === 403 ? '无 senior reviewer 权限' : error?.message ?? '高级审核案件加载失败。');
      }
      return data;
    },
  });
}
