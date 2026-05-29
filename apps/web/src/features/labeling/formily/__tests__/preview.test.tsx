import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { createEmptyPreviewPayload, SchemaFormilyPreviewPanel } from '../preview/SchemaFormilyPreviewPanel';
import { renderClient } from './renderClient';

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
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[]} />);
    expect(view.text()).toContain('Schema 预览');
    expect(view.text()).toContain('暂无字段');
    view.unmount();
  });

  it('re-renders when schemaFields prop changes', () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);

    expect(view.text()).toContain('标题');
    expect(view.text()).not.toContain('分数');

    view.rerender(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'score', type: 'number', label: '分数' })]} />);

    expect(view.text()).toContain('分数');
    view.unmount();
  });

  it('reset action uses an empty local preview AnswerPayload', () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);
    expect(view.text()).toContain('重置预览');
    expect(createEmptyPreviewPayload()).toEqual({});
    view.unmount();
  });
});
