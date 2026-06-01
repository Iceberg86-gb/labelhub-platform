import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type GrantRoleRequest = components['schemas']['GrantRoleRequest'];
export type UserRoleProfile = components['schemas']['LoginUserProfile'];

export function useGrantUserRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation<UserRoleProfile, Error, { userId: number; body: GrantRoleRequest }>({
    mutationFn: async ({ userId, body }) => {
      const { data, error } = await apiClient.POST('/users/{userId}/roles', {
        params: { path: { userId } },
        body,
      });
      if (error || !data) {
        throw new Error(error?.message ?? '用户角色调整失败。');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries();
    },
  });
}
