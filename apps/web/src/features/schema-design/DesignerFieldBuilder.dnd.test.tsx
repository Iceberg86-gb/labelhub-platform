import type { ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaFieldType } from '../../entities/schema/schemaTypes';
import { renderClient } from '../labeling/formily/__tests__/renderClient';

vi.mock('@douyinfe/semi-icons', () => ({
  IconAIWandLevel1: () => <span />,
  IconArticle: () => <span />,
  IconBox: () => <span />,
  IconCalendar: () => <span />,
  IconCheckboxTick: () => <span />,
  IconCode: () => <span />,
  IconDelete: () => <span>delete</span>,
  IconHandle: () => <span>handle</span>,
  IconEyeOpened: () => <span />,
  IconHash: () => <span />,
  IconPlus: () => <span>plus</span>,
  IconRadio: () => <span />,
  IconTabsStroked: () => <span />,
  IconText: () => <span />,
  IconUpload: () => <span />,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, onClick }: { children?: ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>{children}</button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => <section className={className}>{children}</section>,
  Popconfirm: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Popover: ({ children, content }: { children?: ReactNode; content?: ReactNode }) => (
    <div>
      {children}
      {content}
    </div>
  ),
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

import { DesignerFieldBuilder, groupPaletteTypes } from './DesignerFieldBuilder';

describe('DesignerFieldBuilder drag wiring', () => {
  it('groups all field type palette materials without dropping draggable items', () => {
    const view = renderClient(
      <DesignerFieldBuilder
        fields={[{ stableId: 'title', label: 'Title', type: 'text' }]}
        onChange={() => {}}
        onAddField={() => null}
        selectedStableId={null}
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    expect(view.text()).toContain('只读材料');
    expect(view.text()).toContain('选择与约束');
    expect(view.text()).toContain('内容录入');
    expect(view.text()).toContain('容器与高级组件');

    const paletteItems = Array.from(view.container.querySelectorAll('.field-type-palette__item'));
    expect(paletteItems).toHaveLength(13);
    expect(paletteItems.map((item) => item.textContent?.trim())).toEqual([
      '展示项',
      '单选',
      '多选',
      '日期',
      '文本',
      '多行文本',
      '数字',
      '富文本',
      '文件上传',
      '字段分组',
      '标签页组',
      'JSON',
      'AI 交互',
    ]);

    const paletteGroups = Array.from(view.container.querySelectorAll('.field-type-palette__group')).map((group) =>
      Array.from(group.querySelectorAll('.field-type-palette__item')).map((item) => item.textContent?.trim()),
    );
    expect(paletteGroups).toEqual([
      ['展示项'],
      ['单选', '多选', '日期'],
      ['文本', '多行文本', '数字', '富文本', '文件上传'],
      ['字段分组', '标签页组', 'JSON', 'AI 交互'],
    ]);
    view.unmount();
  });

  it('keeps unmapped palette types in a fallback group instead of dropping them', () => {
    const groups = groupPaletteTypes(['show_item', 'text', 'future_widget' as SchemaFieldType]);

    expect(groups).toEqual([
      { title: '只读材料', types: ['show_item'] },
      { title: '内容录入', types: ['text'] },
      { title: '其他', types: ['future_widget'] },
    ]);
  });

  it('uses non-native-button activators for palette and sortable handle wiring', () => {
    const onAddField = vi.fn<(_: SchemaFieldType, _parentStableId?: string, _index?: number) => null>(() => null);
    const view = renderClient(
      <DesignerFieldBuilder
        fields={[{ stableId: 'title', label: 'Title', type: 'text' }]}
        onChange={() => {}}
        onAddField={onAddField}
        selectedStableId={null}
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    const paletteItem = view.container.querySelector('.field-type-palette__item');
    const dragHandle = view.container.querySelector('.field-list-item__drag-handle');
    expect(paletteItem?.tagName).toBe('DIV');
    expect(paletteItem?.getAttribute('role')).toBe('button');
    expect(dragHandle?.tagName).toBe('DIV');
    expect(dragHandle?.getAttribute('role')).toBe('button');
    view.unmount();
  });

  it('keeps the real dnd-kit palette to root drop path wired to onAddField through mouse events', async () => {
    const onAddField = vi.fn<(_: SchemaFieldType, _parentStableId?: string, _index?: number) => null>(() => null);
    const view = renderClient(
      <DesignerFieldBuilder
        fields={[{ stableId: 'title', label: 'Title', type: 'text' }]}
        onChange={() => {}}
        onAddField={onAddField}
        selectedStableId={null}
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    const paletteItem = Array.from(view.container.querySelectorAll('.field-type-palette__item')).find((item) =>
      item.textContent?.includes('文本'),
    ) as HTMLElement;
    const dropzone = view.container.querySelector('.schema-canvas-dropzone') as HTMLElement;
    const fieldItem = view.container.querySelector('.field-list-item') as HTMLElement;
    setRect(paletteItem, rect(10, 10, 120, 40));
    setRect(dropzone, rect(200, 10, 400, 300));
    setRect(fieldItem, rect(210, 20, 360, 52));

    await act(async () => {
      paletteItem.dispatchEvent(mouseEvent('mousedown', { clientX: 20, clientY: 20 }));
    });
    await act(async () => {
      document.dispatchEvent(mouseEvent('mousemove', { clientX: 230, clientY: 38 }));
    });
    await act(async () => {
      document.dispatchEvent(mouseEvent('mousemove', { clientX: 232, clientY: 40 }));
    });
    await act(async () => {
      fieldItem.dispatchEvent(mouseEvent('mouseover', { clientX: 230, clientY: 38 }));
    });
    await act(async () => {
      document.dispatchEvent(mouseEvent('mouseup', { clientX: 230, clientY: 38 }));
    });

    expect(onAddField).toHaveBeenCalledWith('text', undefined, 0);
    view.unmount();
  });
});

function rect(x: number, y: number, width: number, height: number): DOMRect {
  return {
    x,
    y,
    width,
    height,
    top: y,
    left: x,
    right: x + width,
    bottom: y + height,
    toJSON: () => ({}),
  };
}

function setRect(element: HTMLElement, value: DOMRect) {
  element.getBoundingClientRect = () => value;
}

function mouseEvent(type: string, init: MouseEventInit): MouseEvent {
  return new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    button: 0,
    buttons: type === 'mouseup' ? 0 : 1,
    ...init,
  });
}
