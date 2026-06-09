import type { ExportFieldCatalog, TrainingExportFormat } from '../../entities/export/exportTypes';

export type FieldOption = { value: string; label: string };
export type BindingTone = 'neutral' | 'chosen' | 'rejected';
export type BindingSlot = { label: string; source: string; value: string; tone: BindingTone };
export type BindingPreviewResult = { slots: BindingSlot[]; validCount: number; total: number };
export type TrainingProfileBinding = {
  promptSource: string;
  completionSource: string;
  preferenceSource: string;
  choiceASource: string;
  choiceBSource: string;
};

export function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}…` : value;
}

export function buildFieldOptions(catalog?: ExportFieldCatalog): FieldOption[] {
  if (!catalog) {
    return [];
  }
  return catalog.fields.map((field) => {
    const coverage = Math.round((field.nonEmptyRatio ?? 0) * 100);
    const name = field.label || field.source;
    return { value: field.source, label: `${name} · 覆盖 ${coverage}%` };
  });
}

export function buildFieldLabelMap(catalog?: ExportFieldCatalog): Record<string, string> {
  const map: Record<string, string> = {};
  for (const field of catalog?.fields ?? []) {
    map[field.source] = field.label || field.source;
  }
  return map;
}

export function buildMappingRowsFromCatalog(
  catalog: ExportFieldCatalog | undefined,
): Array<{ id: string; source: string; columnName: string; included: boolean }> {
  if (!catalog) {
    return [];
  }
  return catalog.fields.map((field) => ({
    id: field.source,
    source: field.source,
    columnName: field.source.replace(/\./g, '_'),
    included: true,
  }));
}

export function computeBindingPreview(
  format: TrainingExportFormat,
  profile: TrainingProfileBinding,
  sampleRows: Array<Record<string, string>>,
): BindingPreviewResult {
  const total = sampleRows.length;
  if (format === 'flat_table' || total === 0) {
    return { slots: [], validCount: total, total };
  }
  const first = sampleRows[0] ?? {};
  const cell = (row: Record<string, string>, source: string) => (source ? row[source] ?? '' : '');

  if (format === 'trl_dpo_jsonl') {
    const choiceSourceFor = (preferred: string) =>
      preferred === 'A' ? profile.choiceASource : preferred === 'B' ? profile.choiceBSource : '';
    const rejectedSourceFor = (preferred: string) => (preferred === 'A' ? profile.choiceBSource : profile.choiceASource);
    let validCount = 0;
    for (const row of sampleRows) {
      const preferred = cell(row, profile.preferenceSource);
      const prompt = cell(row, profile.promptSource);
      const chosen = cell(row, choiceSourceFor(preferred));
      const rejected = cell(row, rejectedSourceFor(preferred));
      if (prompt && chosen && rejected) {
        validCount += 1;
      }
    }
    const preferredFirst = cell(first, profile.preferenceSource);
    const chosenSource = choiceSourceFor(preferredFirst);
    const rejectedSource = rejectedSourceFor(preferredFirst);
    return {
      total,
      validCount,
      slots: [
        { label: '用户提示', source: profile.promptSource, value: cell(first, profile.promptSource), tone: 'neutral' },
        { label: '优选回答', source: chosenSource, value: cell(first, chosenSource), tone: 'chosen' },
        { label: '拒绝回答', source: rejectedSource, value: cell(first, rejectedSource), tone: 'rejected' },
      ],
    };
  }

  let validCount = 0;
  for (const row of sampleRows) {
    if (cell(row, profile.promptSource) && cell(row, profile.completionSource)) {
      validCount += 1;
    }
  }
  return {
    total,
    validCount,
    slots: [
      { label: '用户提示', source: profile.promptSource, value: cell(first, profile.promptSource), tone: 'neutral' },
      { label: '助手回答', source: profile.completionSource, value: cell(first, profile.completionSource), tone: 'chosen' },
    ],
  };
}
