import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type AuditLog = components['schemas']['AuditLog'];
export type PagedAuditLogs = components['schemas']['PagedAuditLogs'];

export interface AuditLogsFilters {
  page: number;
  size: number;
  actionTypes?: string[];
  resourceTypes?: string[];
  actorUserId?: number;
  resourceId?: number;
  from?: string;
  to?: string;
}

export class AuditLogsQueryError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const csv = (values?: string[]) => (values?.length ? values.join(',') : undefined);

export function useAuditLogsQuery(filters: AuditLogsFilters) {
  return useQuery<PagedAuditLogs, AuditLogsQueryError>({
    queryKey: ['audit-logs', filters],
    staleTime: 15_000,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/audit-logs', {
        params: {
          query: {
            ...filters,
            actionTypes: csv(filters.actionTypes),
            resourceTypes: csv(filters.resourceTypes),
          },
        },
      });

      if (error || !data) throw new AuditLogsQueryError(response.status, error?.message ?? '审计日志加载失败。');
      return data;
    },
  });
}
