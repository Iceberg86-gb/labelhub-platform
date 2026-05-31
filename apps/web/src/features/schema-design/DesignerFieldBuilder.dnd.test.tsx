import type { ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaFieldType } from '../../entities/schema/schemaTypes';
import { renderClient } from '../labeling/formily/__tests__/renderClient';

vi.mock('@douyinfe/semi-icons', () => ({
  IconDelete: () => <span>delete</span>,
  IconHandle: () => <span>handle</span>,
  IconPlus: () => <span>plus</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, onClick }: { children?: ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>{children}</button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => <section className={className}>{children}</section>,
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

import { DesignerFieldBuilder } from './DesignerFieldBuilder';

describe('DesignerFieldBuilder drag wiring', () => {
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

  it('keeps the real dnd-kit palette to root drop path wired to onAddField when jsdom pointer events are available', async () => {
    if (typeof PointerEvent === 'undefined') {
      return;
    }

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

    const paletteItem = view.container.querySelector('.field-type-palette__item') as HTMLElement;
    const dropzone = view.container.querySelector('.schema-canvas-dropzone') as HTMLElement;
    const fieldItem = view.container.querySelector('.field-list-item') as HTMLElement;
    setRect(paletteItem, rect(10, 10, 120, 40));
    setRect(dropzone, rect(200, 10, 400, 300));
    setRect(fieldItem, rect(210, 20, 360, 52));

    await act(async () => {
      paletteItem.dispatchEvent(pointerEvent('pointerdown', { clientX: 20, clientY: 20 }));
    });
    await act(async () => {
      document.dispatchEvent(pointerEvent('pointermove', { clientX: 230, clientY: 38 }));
    });
    await act(async () => {
      document.dispatchEvent(pointerEvent('pointermove', { clientX: 232, clientY: 40 }));
    });
    await act(async () => {
      fieldItem.dispatchEvent(pointerEvent('pointerover', { clientX: 230, clientY: 38 }));
    });
    await act(async () => {
      document.dispatchEvent(pointerEvent('pointerup', { clientX: 230, clientY: 38 }));
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

function pointerEvent(type: string, init: PointerEventInit): PointerEvent {
  return new PointerEvent(type, {
    bubbles: true,
    cancelable: true,
    pointerId: 1,
    pointerType: 'mouse',
    isPrimary: true,
    button: 0,
    buttons: type === 'pointerup' ? 0 : 1,
    ...init,
  });
}
