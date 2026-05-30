import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
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

vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children }: { children?: ReactNode }) => <div data-testid="dnd-context">{children}</div>,
  DragOverlay: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  KeyboardSensor: function KeyboardSensor() {},
  PointerSensor: function PointerSensor() {},
  closestCenter: vi.fn(),
  useDraggable: () => ({
    attributes: {},
    listeners: {},
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
