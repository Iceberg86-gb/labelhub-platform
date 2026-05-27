import { useMutation } from '@tanstack/react-query';
import { Toast } from '@douyinfe/semi-ui';
import { getAccessToken } from '../../shared/api/auth-storage';
import type { AuditLogsFilters } from './useAuditLogsQuery';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

function csvParams(filters: AuditLogsFilters) {
  const params = new URLSearchParams();
  if (filters.actionTypes?.length) params.set('actionTypes', filters.actionTypes.join(','));
  if (filters.resourceTypes?.length) params.set('resourceTypes', filters.resourceTypes.join(','));
  if (filters.actorUserId) params.set('actorUserId', String(filters.actorUserId));
  if (filters.resourceId) params.set('resourceId', String(filters.resourceId));
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  return params.toString();
}

function filename(response: Response) {
  const match = response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/i);
  return match?.[1] ?? `audit-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
}

function triggerDownload(text: string, name: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

export function useAuditLogExportMutation() {
  return useMutation<void, Error, AuditLogsFilters>({
    mutationFn: async (filters) => {
      const query = csvParams(filters);
      const endpoint = `${apiBaseUrl.replace(/\/$/, '')}/audit-logs/export.csv${query ? `?${query}` : ''}`;
      const token = getAccessToken();

      // CSV download needs raw Response/Blob handling, so this hook intentionally uses fetch instead of openapi-fetch.
      const response = await fetch(endpoint, { headers: token ? { Authorization: `Bearer ${token}` } : undefined });
      if (response.status === 413) throw new Error('导出超过 50000 行,请缩小筛选条件后重试');
      if (!response.ok) throw new Error(response.status === 403 ? '权限不足' : '审计日志导出失败,请稍后重试');

      const text = await response.text();
      if (!text.split(/\r?\n/).some((line, index) => index > 0 && line.trim())) {
        Toast.info('未匹配任何审计记录');
        return;
      }
      triggerDownload(text, filename(response));
    },
    onError(error) {
      if (error.message.includes('50000')) Toast.warning(error.message);
      else Toast.error(error.message);
    },
  });
}
