import { useMutation } from '@tanstack/react-query';
import { Toast } from '@douyinfe/semi-ui';
import { authorizedFetch } from '../../shared/api/client';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

type ExportPackageType = 'annotation_results' | 'training_data';

type DownloadExportPackageVariables = {
  snapshotId: number;
  packageType: ExportPackageType;
};

const FALLBACK_FILENAME: Record<ExportPackageType, string> = {
  annotation_results: 'labelhub-annotation-results.zip',
  training_data: 'labelhub-training-data.zip',
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

function errorMessage(status: number) {
  if (status === 409) return '当前快照没有有效训练数据,请选择训练格式重新导出';
  if (status === 404) return '快照不存在或无权访问';
  return '下载失败,请稍后重试';
}

export function useDownloadExportPackageMutation() {
  return useMutation<void, Error, DownloadExportPackageVariables>({
    mutationFn: async ({ snapshotId, packageType }) => {
      const endpoint = `${apiBaseUrl.replace(/\/$/, '')}/exports/snapshots/${snapshotId}/packages/${packageType}`;
      const response = await authorizedFetch(endpoint);
      if (!response.ok) {
        throw new Error(errorMessage(response.status));
      }
      const blob = await response.blob();
      triggerDownload(blob, filename(response, FALLBACK_FILENAME[packageType]));
    },
    onError(error) {
      Toast.error(error.message);
    },
  });
}
