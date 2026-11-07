import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { AgentRunSummary, AgentTrajectoryStep, McpToken, McpTokenIssued, McpTokenIssueRequest } from './agentAccessTypes';

/** ADR-022: owner-side MCP token management and the external agent trajectory viewer. */
export const mcpTokensQueryKey = (taskId: number) => ['tasks', taskId, 'mcp-tokens'] as const;
export const agentRunsQueryKey = (taskId: number) => ['tasks', taskId, 'agent-runs'] as const;
export const agentRunStepsQueryKey = (runId: string) => ['agent-runs', runId, 'steps'] as const;

function fail(error: unknown, fallback: string): Error {
  const body = error as { message?: string } | undefined;
  return new Error(body?.message ?? fallback);
}

export function useMcpTokensQuery(taskId: number) {
  return useQuery<McpToken[]>({
    queryKey: mcpTokensQueryKey(taskId),
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/mcp-tokens', { params: { path: { taskId } } });
      if (error || !data) throw fail(error, '令牌列表加载失败');
      return data;
    },
  });
}

export function useIssueMcpTokenMutation(taskId: number) {
  const queryClient = useQueryClient();
  return useMutation<McpTokenIssued, Error, McpTokenIssueRequest>({
    mutationFn: async (body) => {
      const { data, error } = await apiClient.POST('/tasks/{taskId}/mcp-tokens', { params: { path: { taskId } }, body });
      if (error || !data) throw fail(error, '签发令牌失败');
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mcpTokensQueryKey(taskId) }),
  });
}

export function useRevokeMcpTokenMutation(taskId: number) {
  const queryClient = useQueryClient();
  return useMutation<McpToken, Error, number>({
    mutationFn: async (tokenId) => {
      const { data, error } = await apiClient.POST('/mcp-tokens/{tokenId}/revoke', { params: { path: { tokenId } } });
      if (error || !data) throw fail(error, '撤销令牌失败');
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mcpTokensQueryKey(taskId) }),
  });
}

export function useAgentRunsQuery(taskId: number) {
  return useQuery<AgentRunSummary[]>({
    queryKey: agentRunsQueryKey(taskId),
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/agent-runs', { params: { path: { taskId } } });
      if (error || !data) throw fail(error, '运行记录加载失败');
      return data;
    },
  });
}

export function useAgentRunStepsQuery(runId: string | undefined) {
  return useQuery<AgentTrajectoryStep[]>({
    queryKey: agentRunStepsQueryKey(runId ?? ''),
    enabled: Boolean(runId),
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/agent-runs/{runId}/steps', { params: { path: { runId: runId as string } } });
      if (error || !data) throw fail(error, '轨迹加载失败');
      return data;
    },
  });
}
