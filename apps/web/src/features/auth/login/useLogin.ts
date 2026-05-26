import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';
import { saveSession } from '../../../shared/api/auth-storage';
import { defaultPathForRoles } from '../../../shared/auth/roleRoutes';

export type LoginValues = components['schemas']['LoginRequest'];

export type LoginFailure = {
  field?: keyof LoginValues;
  message: string;
};

function normalizeLoginError(error: unknown): LoginFailure {
  if (error && typeof error === 'object' && 'message' in error) {
    return error as LoginFailure;
  }

  return { message: '登录失败，请稍后重试。' };
}

export function useLogin() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation<void, LoginFailure, LoginValues>({
    mutationFn: async (values) => {
      const { data, error, response } = await apiClient.POST('/auth/login', {
        body: values,
      });

      if (response.status === 401) {
        throw { field: 'password', message: '用户名或密码错误' } satisfies LoginFailure;
      }

      if (error || !data) {
        throw {
          message: error?.message ?? '登录失败，请稍后重试。',
        } satisfies LoginFailure;
      }

      saveSession(data.accessToken, data.expiresAt, data.user);
      queryClient.clear();
      await Promise.resolve();
      navigate(defaultPathForRoles(data.user.roles), { replace: true });
    },
  });

  return {
    ...mutation,
    normalizeError: normalizeLoginError,
  };
}
