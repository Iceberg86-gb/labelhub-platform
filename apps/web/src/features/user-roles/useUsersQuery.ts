import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type UserAccountSummary = components['schemas']['UserAccountSummary'];
export type PagedUsers = components['schemas']['PagedUsers'];

type UsersQueryParams = {
  page: number;
  size: number;
};

export const usersQueryKey = (params: UsersQueryParams) => ['users', params];

export function useUsersQuery(params: UsersQueryParams) {
  return useQuery<PagedUsers>({
    queryKey: usersQueryKey(params),
    staleTime: 15_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/users', {
        params: { query: params },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '注册账号列表加载失败。');
      }

      return data;
    },
  });
}
