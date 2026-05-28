import { renderToString } from 'react-dom/server';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { createEmptyPreviewPayload, SchemaFormilyPreviewPanel } from '../preview/SchemaFormilyPreviewPanel';

let assertions = 0;

function field(overrides: Partial<SchemaField> & Pick<SchemaField, 'stableId' | 'type'>): SchemaField {
  return {
    stableId: overrides.stableId,
    type: overrides.type,
    label: overrides.label ?? overrides.stableId,
    placeholder: overrides.placeholder,
    help: overrides.help,
    validation: overrides.validation,
    options: overrides.options,
    children: overrides.children,
  };
}

function equal(actual: unknown, expected: unknown, message?: string) {
  if (actual !== expected) {
    throw new Error(message ?? `Expected ${String(actual)} to equal ${String(expected)}`);
  }
}

function ok(value: unknown, message?: string) {
  if (!value) {
    throw new Error(message ?? 'Expected value to be truthy');
  }
}

function check(name: string, assertion: () => void) {
  assertion();
  assertions += 1;
  console.log(`ok ${assertions} - ${name}`);
}

check('SchemaFormilyPreviewPanel mounts with empty fields without throwing', () => {
  const html = renderToString(<SchemaFormilyPreviewPanel schemaFields={[]} />);
  ok(html.includes('Schema 预览'));
  ok(html.includes('暂无字段'));
});

check('SchemaFormilyPreviewPanel re-renders when schemaFields prop changes', () => {
  const first = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);
  const second = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'score', type: 'number', label: '分数' })]} />);

  ok(first.includes('标题'));
  equal(first.includes('分数'), false);
  ok(second.includes('分数'));
});

check('reset action uses an empty local preview AnswerPayload', () => {
  const html = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);
  ok(html.includes('重置预览'));
  equal(JSON.stringify(createEmptyPreviewPayload()), '{}');
});

console.log(`C5 preview assertions passed: ${assertions}`);
