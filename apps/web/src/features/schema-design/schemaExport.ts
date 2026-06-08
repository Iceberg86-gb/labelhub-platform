import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type SchemaExportPackage = components['schemas']['SchemaExportPackage'];

export async function exportSchemaVersionPackage(schemaId: number, versionId: number): Promise<SchemaExportPackage> {
  const { data, error } = await apiClient.GET('/schemas/{schemaId}/versions/{versionId}/export', {
    params: {
      path: { schemaId, versionId },
    },
  });

  if (error || !data) {
    throw new Error(error?.message ?? '模板导出失败。');
  }

  return data;
}

export function downloadSchemaPackage(pkg: SchemaExportPackage) {
  const filename = `${sanitizeFilename(pkg.name || 'labelhub-schema')}-v${pkg.versionNumber}.labelhub-schema.json`;
  const blob = new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function sanitizeFilename(value: string) {
  return value.trim().replace(/[^\w\u4e00-\u9fa5.-]+/g, '-').replace(/^-+|-+$/g, '') || 'labelhub-schema';
}
