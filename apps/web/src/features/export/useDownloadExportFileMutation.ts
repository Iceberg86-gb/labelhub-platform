import { useMutation } from '@tanstack/react-query';
import { Toast } from '@douyinfe/semi-ui';
import { getAccessToken } from '../../shared/api/auth-storage';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

type DownloadExportFileVariables = {
  snapshotId: number;
  fileName: string;
};

function filename(response: Response, fallback: string) {
  const match = response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/i);
  return match?.[1] ?? fallback;
}

function triggerDownload(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

export function useDownloadExportFileMutation() {
  return useMutation<void, Error, DownloadExportFileVariables>({
    mutationFn: async ({ snapshotId, fileName }) => {
      const endpoint = `${apiBaseUrl.replace(/\/$/, '')}/exports/snapshots/${snapshotId}/files/${encodeURIComponent(fileName)}`;
      const token = getAccessToken();
      const response = await fetch(endpoint, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!response.ok) {
        throw new Error(response.status === 404 ? '导出文件不存在或无权访问' : '下载失败,请稍后重试');
      }
      const blob = await response.blob();
      triggerDownload(blob, filename(response, fileName));
    },
    onError(error) {
      Toast.error(error.message);
    },
  });
}
