import type { SchemaField } from '../../entities/schema/schemaTypes';

export function resolveShowItemValue(field: SchemaField | undefined, itemPayload: unknown): unknown {
  const sourcePath = field?.sourcePath?.trim();
  if (sourcePath) {
    const sourcedValue = readPath(itemPayload, sourcePath);
    if (hasDisplayValue(sourcedValue)) {
      return sourcedValue;
    }
  }
  return field?.content ?? field?.help ?? '';
}

export function formatShowItemValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function readPath(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, segment) => {
    if (current === null || current === undefined || !segment) return undefined;
    if (Array.isArray(current) && /^\d+$/.test(segment)) {
      return current[Number(segment)];
    }
    if (typeof current === 'object' && Object.hasOwn(current, segment)) {
      return (current as Record<string, unknown>)[segment];
    }
    return undefined;
  }, value);
}

function hasDisplayValue(value: unknown): boolean {
  return value !== null && value !== undefined && (typeof value !== 'string' || value.trim().length > 0);
}
