import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';
import { saveSession } from '../../../shared/api/auth-storage';
import { defaultPathForRoles } from '../../../shared/auth/roleRoutes';

export type RegisterValues = components['schemas']['RegisterRequest'];

export type RegisterFailure = {
  field?: keyof RegisterValues;
  message: string;
};

function normalizeRegisterError(error: unknown): RegisterFailure {
  if (error && typeof error === 'object' && 'message' in error) {
    return error as RegisterFailure;
  }

  return { message: '注册失败，请稍后重试。' };
}

export function useRegister() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation<void, RegisterFailure, RegisterValues>({
    mutationFn: async (values) => {
      const { data, error } = await apiClient.POST('/auth/register', {
        body: values,
      });

      if (error || !data) {
        const fieldError = error?.fieldErrors?.[0];
        throw {
          field: fieldError?.field as keyof RegisterValues | undefined,
          message: fieldError?.message ?? error?.message ?? '注册失败，请稍后重试。',
        } satisfies RegisterFailure;
      }

      saveSession(data.accessToken, data.expiresAt, data.user);
      queryClient.clear();
      await Promise.resolve();
      navigate(defaultPathForRoles(data.user.roles), { replace: true });
    },
  });

  return {
    ...mutation,
    normalizeError: normalizeRegisterError,
  };
}
