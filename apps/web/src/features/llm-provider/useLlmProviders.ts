import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type LlmProviderConfig = components['schemas']['LlmProviderConfig'];
export type LlmProviderConfigRequest = components['schemas']['LlmProviderConfigRequest'];
export type LlmProviderTestConnectionRequest = components['schemas']['LlmProviderTestConnectionRequest'];
export type LlmProviderTestConnectionResponse = components['schemas']['LlmProviderTestConnectionResponse'];

export const llmProvidersQueryKey = ['llm-providers'] as const;

export function useLlmProvidersQuery() {
  return useQuery<LlmProviderConfig[]>({
    queryKey: llmProvidersQueryKey,
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/llm/providers');
      if (error || !data) {
        throw new Error(error?.message ?? 'LLM Provider 列表加载失败。');
      }
      return data;
    },
  });
}

export function useSaveLlmProviderMutation() {
  const queryClient = useQueryClient();

  return useMutation<LlmProviderConfig, Error, { id?: number; body: LlmProviderConfigRequest }>({
    mutationFn: async ({ id, body }) => {
      const response = id == null
        ? await apiClient.POST('/llm/providers', { body })
        : await apiClient.PATCH('/llm/providers/{providerConfigId}', {
            params: { path: { providerConfigId: id } },
            body,
          });

      if (response.error || !response.data) {
        throw new Error(response.error?.message ?? 'LLM Provider 保存失败。');
      }
      return response.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: llmProvidersQueryKey });
    },
  });
}

export function useDeleteLlmProviderMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (id) => {
      const { error } = await apiClient.DELETE('/llm/providers/{providerConfigId}', {
        params: { path: { providerConfigId: id } },
      });
      if (error) {
        throw new Error(error.message ?? 'LLM Provider 删除失败。');
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: llmProvidersQueryKey });
    },
  });
}

export function useTestLlmProviderMutation() {
  return useMutation<LlmProviderTestConnectionResponse, Error, { id?: number; body: LlmProviderTestConnectionRequest }>({
    mutationFn: async ({ id, body }) => {
      const response = id == null
        ? await apiClient.POST('/llm/providers:test-connection', { body })
        : await apiClient.POST('/llm/providers/{providerConfigId}:test-connection', {
            params: { path: { providerConfigId: id } },
            body,
          });

      if (response.error || !response.data) {
        throw new Error(response.error?.message ?? 'LLM Provider 连接测试失败。');
      }
      return response.data;
    },
  });
}
