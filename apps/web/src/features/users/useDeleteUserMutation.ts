import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';

export function useDeleteUserMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (userId) => {
      const { error } = await apiClient.DELETE('/users/{userId}', {
        params: { path: { userId } },
      });
      if (error) {
        throw new Error(error.message ?? '账号停用失败。');
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}
