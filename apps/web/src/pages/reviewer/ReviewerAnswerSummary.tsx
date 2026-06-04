import type { AnswerPayload, AnswerValue } from '../../entities/submission/answerPayload';
import { isAnswerPayload, isEmptyAnswerValue } from '../../entities/submission/answerPayload';
import type { SchemaField } from '../../entities/schema/schemaTypes';

export interface ReviewerAnswerSummaryProps {
  schemaFields: SchemaField[];
  answerPayload: AnswerPayload;
  itemPayload?: unknown;
}

type ReviewerAnswerRow = {
  key: string;
  label: string;
  type: SchemaField['type'] | 'unknown';
  value: AnswerValue | undefined;
  options?: SchemaField['options'];
  displayValue?: unknown;
};

const SCORE_FIELD_TYPES = new Set<SchemaField['type']>(['number', 'single_select']);

export function ReviewerAnswerSummary({ schemaFields, answerPayload, itemPayload }: ReviewerAnswerSummaryProps) {
  const rows = flattenAnswerRows(schemaFields, answerPayload, itemPayload);
  const scoreRows = rows.filter((row) => SCORE_FIELD_TYPES.has(row.type as SchemaField['type']));
  const contentRows = rows.filter((row) => !SCORE_FIELD_TYPES.has(row.type as SchemaField['type']));

  return (
    <section className="reviewer-answer-summary" aria-label="标注员答案摘要">
      {scoreRows.length > 0 ? (
        <div className="reviewer-answer-score-strip" aria-label="维度分">
          {scoreRows.map((row) => (
            <span className="reviewer-answer-score-pill" key={row.key}>
              <span>{row.label}</span>
              <strong>{formatAnswerValue(row.value, row.options)}</strong>
            </span>
          ))}
        </div>
      ) : null}

      <div className="reviewer-answer-list">
        {contentRows.map((row) => (
          <ReviewerAnswerItem key={row.key} row={row} />
        ))}
      </div>
    </section>
  );
}

function ReviewerAnswerItem({ row }: { row: ReviewerAnswerRow }) {
  const displayValue = formatAnswerValue(row.displayValue ?? row.value, row.options);
  const itemClassName = [
    'reviewer-answer-item',
    `reviewer-answer-item--${row.type}`,
    isPrimaryAnswerRow(row) ? 'reviewer-answer-item--hero' : '',
    displayValue === '未填写' ? 'reviewer-answer-item--empty' : '',
  ].filter(Boolean).join(' ');

  if (row.type === 'json_editor') {
    return (
      <details className={itemClassName}>
        <summary>{row.label}</summary>
        <pre>{formatAnswerValue(row.value, row.options)}</pre>
      </details>
    );
  }

  return (
    <div className={itemClassName}>
      <span className="reviewer-answer-item__label">{row.label}</span>
      <p className="reviewer-answer-item__value">{displayValue}</p>
    </div>
  );
}

function isPrimaryAnswerRow(row: ReviewerAnswerRow) {
  return row.type === 'show_item' || row.label === '题目' || row.label === '模型回答' || row.label === '参考答案';
}

function flattenAnswerRows(
  fields: SchemaField[],
  payload: AnswerPayload,
  itemPayload: unknown,
  parents: string[] = [],
  visited = new Set<string>(),
  includeExtras = true,
): ReviewerAnswerRow[] {
  const rows = fields.flatMap((field) => {
    if (field.type === 'nested_object') {
      visited.add(field.stableId);
      const nestedPayload = isAnswerPayload(payload[field.stableId]) ? payload[field.stableId] as AnswerPayload : {};
      return flattenAnswerRows(field.children ?? [], nestedPayload, itemPayload, [...parents, field.label], visited, true);
    }

    if (field.type === 'tab_container') {
      visited.add(field.stableId);
      return (field.tabs ?? []).flatMap((tab) =>
        flattenAnswerRows(tab.children ?? [], payload, itemPayload, [...parents, field.label, tab.label], visited, false),
      );
    }

    visited.add(field.stableId);
    return [{
      key: [...parents, field.stableId].join('/'),
      label: [...parents, field.label].join(' · '),
      type: field.type,
      value: payload[field.stableId],
      options: field.options,
      displayValue: field.type === 'show_item' ? resolveShowItemValue(field, itemPayload) : undefined,
    }];
  });

  if (!includeExtras) {
    return rows;
  }

  const knownIds = new Set(Array.from(visited));
  const extraRows = Object.entries(payload)
    .filter(([stableId]) => !knownIds.has(stableId))
    .map(([stableId, value]) => ({
      key: `extra/${stableId}`,
      label: stableId,
      type: 'unknown' as const,
      value,
    }));

  return [...rows, ...extraRows];
}

function formatAnswerValue(value: unknown, options?: SchemaField['options']): string {
  if (isEmptyAnswerValue(value)) {
    return '未填写';
  }
  if (typeof value === 'string') {
    return optionLabel(value, options) ?? value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((entry) => (typeof entry === 'string' ? optionLabel(entry, options) ?? entry : formatAnswerValue(entry))).join('、');
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function optionLabel(value: string, options?: SchemaField['options']) {
  return options?.find((option) => option.value === value)?.label;
}

function resolveShowItemValue(field: SchemaField, itemPayload: unknown): unknown {
  const sourcePath = field.sourcePath?.trim();
  if (sourcePath) {
    const sourcedValue = readPath(itemPayload, sourcePath);
    if (sourcedValue !== null && sourcedValue !== undefined && (typeof sourcedValue !== 'string' || sourcedValue.trim().length > 0)) {
      return sourcedValue;
    }
  }
  return field.content ?? field.help ?? '';
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
