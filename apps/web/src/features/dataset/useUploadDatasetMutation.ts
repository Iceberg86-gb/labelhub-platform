import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Dataset, DatasetImportFormat } from '../../entities/dataset/datasetTypes';
import { getAccessToken } from '../../shared/api/auth-storage';
import { datasetsQueryKey } from './useDatasetsQuery';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

export type UploadDatasetVariables = {
  taskId: number;
  file: File;
  sourceName?: string;
  format?: DatasetImportFormat;
};

export class UploadDatasetFailure extends Error {
  constructor(public readonly status: number, public readonly code: string | undefined, public readonly userMessage: string) {
    super(userMessage);
  }
}

const uploadErrorMessages: Record<string, string> = {
  EMPTY_DATASET: '数据集为空,请检查文件内容',
};

export function useUploadDatasetMutation() {
  const queryClient = useQueryClient();

  return useMutation<Dataset, UploadDatasetFailure, UploadDatasetVariables>({
    mutationFn: async ({ taskId, file, sourceName, format }) => {
      const token = getAccessToken();
      if (!token) {
        throw new UploadDatasetFailure(401, 'UNAUTHORIZED', '登录已过期,请重新登录');
      }

      const formData = new FormData();
      formData.append('file', file);
      formData.append('taskId', String(taskId));
      if (sourceName) formData.append('sourceName', sourceName);
      if (format) formData.append('format', format);

      const response = await fetch(`${API_BASE}/datasets`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: formData });

      if (!response.ok) {
        const body = await response.json().catch(() => ({} as { code?: string; message?: string }));
        throw new UploadDatasetFailure(response.status, body.code, mapUploadErrorMessage(response.status, body.code, body.message));
      }

      return (await response.json()) as Dataset;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: datasetsQueryKey(variables.taskId) });
    },
  });
}

function mapUploadErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (code && uploadErrorMessages[code]) return uploadErrorMessages[code];
  if (status === 400 && code === 'INVALID_DATASET_FILE') {
    return message ?? '文件格式不正确';
  }
  if (status === 404) return '任务不存在或无权访问';
  if (status === 413) return '文件过大,单次上传不能超过 10MB';
  return message ?? '上传失败,请稍后重试';
}
