import type { components } from '../../shared/api/generated/schema';

export type Dataset = components['schemas']['Dataset'];
export type PagedDatasets = components['schemas']['PagedDatasets'];
export type DatasetImportFormat = components['schemas']['DatasetImportFormat'];
export type UpdateTaskCurrentDatasetRequest = components['schemas']['UpdateTaskCurrentDatasetRequest'];

export const DATASET_IMPORT_STATUS_LABELS: Record<string, string> = {
  completed: '已完成',
  failed: '失败',
  created: '处理中',
};
