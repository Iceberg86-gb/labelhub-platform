import { renderToString } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { createEmptyPreviewPayload, SchemaFormilyPreviewPanel } from '../preview/SchemaFormilyPreviewPanel';

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

describe('SchemaFormilyPreviewPanel', () => {
  it('mounts with empty fields without throwing', () => {
    const html = renderToString(<SchemaFormilyPreviewPanel schemaFields={[]} />);
    expect(html).toContain('Schema 预览');
    expect(html).toContain('暂无字段');
  });

  it('re-renders when schemaFields prop changes', () => {
    const first = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);
    const second = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'score', type: 'number', label: '分数' })]} />);

    expect(first).toContain('标题');
    expect(first).not.toContain('分数');
    expect(second).toContain('分数');
  });

  it('reset action uses an empty local preview AnswerPayload', () => {
    const html = renderToString(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);
    expect(html).toContain('重置预览');
    expect(createEmptyPreviewPayload()).toEqual({});
  });
});
