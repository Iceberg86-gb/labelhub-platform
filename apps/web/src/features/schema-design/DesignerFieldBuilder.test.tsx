import type { MouseEvent, ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { renderClient } from '../labeling/formily/__tests__/renderClient';

vi.mock('@douyinfe/semi-icons', () => ({
  IconAIWandLevel1: () => <span />,
  IconArticle: () => <span />,
  IconBox: () => <span />,
  IconCalendar: () => <span />,
  IconCheckboxTick: () => <span />,
  IconCode: () => <span />,
  IconCopy: () => <span>copy</span>,
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
  Button: ({
    children,
    className,
    disabled,
    'aria-label': ariaLabel,
    onClick,
  }: {
    children?: ReactNode;
    className?: string;
    disabled?: boolean;
    'aria-label'?: string;
    onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
  }) => (
    <button aria-label={ariaLabel} className={className} disabled={disabled} type="button" onClick={onClick}>
      {children}
    </button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => <section className={className}>{children}</section>,
  Popconfirm: ({
    children,
    content,
    onConfirm,
    title,
  }: {
    children?: ReactNode;
    content?: ReactNode;
    onConfirm?: () => void;
    title?: ReactNode;
  }) => (
    <span>
      {children}
      <span>{title}</span>
      <span>{content}</span>
      <button type="button" onClick={onConfirm}>
        确认删除字段
      </button>
    </span>
  ),
  Popover: ({ children, content }: { children?: ReactNode; content?: ReactNode }) => (
    <div>
      {children}
      {content}
    </div>
  ),
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tooltip: ({ children, content }: { children?: ReactNode; content?: ReactNode }) => (
    <span>
      {children}
      <span>{content}</span>
    </span>
  ),
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children }: { children?: ReactNode }) => <div data-testid="dnd-context">{children}</div>,
  DragOverlay: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  KeyboardSensor: function KeyboardSensor() {},
  MouseSensor: function MouseSensor() {},
  TouchSensor: function TouchSensor() {},
  closestCenter: vi.fn(),
  useDraggable: () => ({
    attributes: {},
    listeners: {},
    setActivatorNodeRef: vi.fn(),
    setNodeRef: vi.fn(),
    transform: null,
    isDragging: false,
  }),
  useDroppable: ({ id }: { id: string }) => ({
    isOver: false,
    setNodeRef: vi.fn(),
    active: { id },
  }),
  useSensor: () => ({}),
  useSensors: (...sensors: unknown[]) => sensors,
}));

vi.mock('@dnd-kit/sortable', () => ({
  SortableContext: ({ children }: { children?: ReactNode }) => <div data-testid="sortable-context">{children}</div>,
  arrayMove: <T,>(items: T[], from: number, to: number) => {
    const copy = [...items];
    const [item] = copy.splice(from, 1);
    copy.splice(to, 0, item);
    return copy;
  },
  sortableKeyboardCoordinates: vi.fn(),
  useSortable: () => ({
    attributes: {},
    listeners: {},
    setActivatorNodeRef: vi.fn(),
    setNodeRef: vi.fn(),
    transform: null,
    transition: undefined,
    isDragging: false,
  }),
  verticalListSortingStrategy: {},
}));

import { DesignerFieldBuilder } from './DesignerFieldBuilder';

describe('DesignerFieldBuilder nested canvas rendering', () => {
  it('renders nested object children and tab children in the canvas tree', () => {
    const fields = makeFields();
    const view = renderClient(
      <DesignerFieldBuilder
        fields={fields}
        onChange={() => {}}
        onAddField={() => null}
        selectedStableId="child"
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map([['child', [{ fieldPath: 'fields[1].children[0]', stableId: 'child', reason: 'required' }]]])}
        validationErrorCount={1}
      />,
    );

    expect(view.text()).toContain('Group');
    expect(view.text()).toContain('Child note');
    expect(view.text()).toContain('First tab');
    expect(view.text()).toContain('Tab answer');
    expect(view.html()).toContain('field-list-item--selected');
    expect(view.html()).toContain('field-list-item--error');
    view.unmount();
  });

  it('exposes the workspace visual hooks for material rail, canvas, and drag feedback', () => {
    const view = renderClient(
      <DesignerFieldBuilder
        fields={makeFields()}
        onChange={() => {}}
        onAddField={() => null}
        selectedStableId="child"
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map([['child', [{ fieldPath: 'fields[1].children[0]', stableId: 'child', reason: 'required' }]]])}
        validationErrorCount={1}
      />,
    );

    expect(view.html()).toContain('schema-material-panel schema-material-panel--rail');
    expect(view.html()).toContain('field-type-palette__drag-hint');
    expect(view.html()).toContain('schema-canvas-panel schema-canvas-panel--workspace');
    expect(view.html()).toContain('schema-canvas-dropzone--root');
    expect(view.html()).toContain('schema-canvas-child-container');
    expect(view.html()).toContain('schema-canvas-tab-pane__label');
    expect(view.html()).toContain('field-list-item--selected');
    view.unmount();
  });

  it('keeps field delete actions hidden until row interaction and requires confirmation', () => {
    const onDelete = vi.fn();
    const view = renderClient(
      <DesignerFieldBuilder
        fields={makeFields()}
        onChange={() => {}}
        onAddField={() => null}
        selectedStableId="child"
        onSelect={() => {}}
        onDelete={onDelete}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    expect(view.html()).toContain('field-list-item__delete-action');
    expect(view.html()).toContain('field-list-item__delete-action--deferred');
    expect(view.text()).toContain('删除字段?');

    const deleteButton = view.container.querySelector('[aria-label="删除字段"]') as HTMLButtonElement;
    act(() => {
      deleteButton.click();
    });
    expect(onDelete).not.toHaveBeenCalled();

    const confirmButton = Array.from(view.container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('确认删除字段'),
    ) as HTMLButtonElement;
    act(() => {
      confirmButton.click();
    });

    expect(onDelete).toHaveBeenCalledWith('title');
    view.unmount();
  });

  it('copies leaf fields next to the original with fresh ids and independent nested objects', () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    const fields: SchemaField[] = [
      {
        stableId: 'choice',
        label: '偏好选择',
        type: 'single_select',
        options: [{ label: 'A', value: 'a' }],
        visibleWhen: { field: 'driver', op: 'eq', value: 'show' },
      },
      { stableId: 'note', label: '说明', type: 'text' },
    ];
    const view = renderClient(
      <DesignerFieldBuilder
        fields={fields}
        onChange={onChange}
        onAddField={() => null}
        selectedStableId="choice"
        onSelect={onSelect}
        onDelete={() => {}}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    const copyButton = view.container.querySelector('[aria-label="复制字段"]') as HTMLButtonElement;
    act(() => {
      copyButton.click();
    });

    const nextFields = onChange.mock.calls.at(-1)?.[0] as SchemaField[];
    expect(nextFields.map((field) => field.label)).toEqual(['偏好选择', '偏好选择 (副本)', '说明']);
    expect(nextFields[1].stableId).not.toBe('choice');
    expect(nextFields[1].stableId).toBeTruthy();
    expect(nextFields[1].options).toEqual(fields[0].options);
    expect(nextFields[1].options).not.toBe(fields[0].options);
    expect(nextFields[1].visibleWhen).toEqual(fields[0].visibleWhen);
    expect(nextFields[1].visibleWhen).not.toBe(fields[0].visibleWhen);
    expect(onSelect).toHaveBeenCalledWith(nextFields[1].stableId);
    view.unmount();
  });

  it('keeps container field copy disabled with a clear tooltip', () => {
    const view = renderClient(
      <DesignerFieldBuilder
        fields={makeFields()}
        onChange={() => {}}
        onAddField={() => null}
        selectedStableId="group"
        onSelect={() => {}}
        onDelete={() => {}}
        errors={new Map()}
        validationErrorCount={0}
      />,
    );

    const copyButtons = Array.from(view.container.querySelectorAll('[aria-label="复制字段"]')) as HTMLButtonElement[];
    const groupCopyButton = copyButtons.find((button) => button.closest('.field-list-item')?.textContent?.includes('Group'));
    expect(groupCopyButton?.disabled).toBe(true);
    expect(view.text()).toContain('暂不支持复制容器字段');
    view.unmount();
  });
});

function makeFields(): SchemaField[] {
  return [
    { stableId: 'title', label: 'Title', type: 'text' },
    {
      stableId: 'group',
      label: 'Group',
      type: 'nested_object',
      children: [{ stableId: 'child', label: 'Child note', type: 'text' }],
    },
    {
      stableId: 'tabs',
      label: 'Tabs',
      type: 'tab_container',
      tabs: [{ stableId: 'tab-a', label: 'First tab', children: [{ stableId: 'tab-child', label: 'Tab answer', type: 'text' }] }],
    },
  ];
}
