import { useQuery } from '@tanstack/react-query';
import type { components } from '../../shared/api/generated/schema';
import { apiClient } from '../../shared/api/client';

export type PromptVersion = components['schemas']['PromptVersion'];

export const defaultPromptVersionQueryKey = ['prompt-versions', 'default'] as const;

export async function fetchDefaultPromptVersion(): Promise<PromptVersion> {
  const { data, error } = await apiClient.GET('/prompt-versions/default');

  if (error || !data) {
    const body = error as { message?: string } | undefined;
    throw new Error(body?.message ?? '默认 Prompt 版本不可用');
  }

  return data;
}

export function useDefaultPromptVersionQuery(enabled = true) {
  return useQuery({
    queryKey: defaultPromptVersionQueryKey,
    queryFn: fetchDefaultPromptVersion,
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}
