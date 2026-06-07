import type { DatasetItem } from '../../entities/dataset/datasetTypes';

export type SourcePathOption = {
  value: string;
  typeLabel: string;
};

const MAX_DEPTH = 5;
const MAX_OPTIONS = 120;

export function buildSourcePathOptionsFromDatasetItems(items: DatasetItem[]): SourcePathOption[] {
  const paths = new Map<string, Set<string>>();

  items.slice(0, 20).forEach((item) => {
    collectSourcePaths(item.itemPayload, '', paths, 0);
  });

  return Array.from(paths.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .slice(0, MAX_OPTIONS)
    .map(([value, typeLabels]) => ({ value, typeLabel: Array.from(typeLabels).sort().join(' / ') }));
}

function collectSourcePaths(value: unknown, prefix: string, paths: Map<string, Set<string>>, depth: number) {
  if (!prefix && !isPlainObject(value)) return;
  if (depth > MAX_DEPTH) return;

  if (prefix) {
    addPath(paths, prefix, sourceValueType(value));
  }

  if (Array.isArray(value)) {
    const firstItem = value[0];
    if (firstItem !== undefined) {
      collectSourcePaths(firstItem, prefix ? `${prefix}.0` : '0', paths, depth + 1);
    }
    return;
  }

  if (!isPlainObject(value)) return;

  Object.entries(value as Record<string, unknown>).forEach(([key, child]) => {
    const childPath = prefix ? `${prefix}.${key}` : key;
    collectSourcePaths(child, childPath, paths, depth + 1);
  });
}

function addPath(paths: Map<string, Set<string>>, path: string, typeLabel: string) {
  const labels = paths.get(path) ?? new Set<string>();
  labels.add(typeLabel);
  paths.set(path, labels);
}

function sourceValueType(value: unknown): string {
  if (Array.isArray(value)) return 'array';
  if (value === null) return 'null';
  return typeof value;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}
