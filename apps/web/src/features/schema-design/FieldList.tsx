import { Button, Tag, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconHandle } from '@douyinfe/semi-icons';
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import type { CSSProperties } from 'react';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPE_LABELS } from '../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../entities/schema/schemaValidation';

type FieldListProps = {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
  selectedStableId: string | null;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
  errors: Map<string, FieldValidationError[]>;
};

export function FieldList({ fields, onChange, selectedStableId, onSelect, onDelete, errors }: FieldListProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;

    const oldIndex = fields.findIndex((field) => field.stableId === active.id);
    const newIndex = fields.findIndex((field) => field.stableId === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    onChange(arrayMove(fields, oldIndex, newIndex));
  };

  if (fields.length === 0) {
    return (
      <div className="field-list-empty">
        <Typography.Text type="tertiary">还没有字段。</Typography.Text>
      </div>
    );
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={fields.map((field) => field.stableId)} strategy={verticalListSortingStrategy}>
        <div className="field-list">
          {fields.map((field) => (
            <SortableFieldItem
              key={field.stableId}
              field={field}
              selected={field.stableId === selectedStableId}
              hasError={errors.has(field.stableId)}
              onSelect={onSelect}
              onDelete={onDelete}
            />
          ))}
        </div>
      </SortableContext>
    </DndContext>
  );
}

type SortableFieldItemProps = {
  field: SchemaField;
  selected: boolean;
  hasError: boolean;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
};

export function SortableFieldItem({ field, selected, hasError, onSelect, onDelete }: SortableFieldItemProps) {
  const { attributes, listeners, setActivatorNodeRef, setNodeRef, transform, transition, isDragging } = useSortable({ id: field.stableId });
  const transformStyle = transform
    ? `translate3d(${Math.round(transform.x)}px, ${Math.round(transform.y)}px, 0) scaleX(${transform.scaleX}) scaleY(${transform.scaleY})`
    : undefined;
  const style: CSSProperties = { transform: transformStyle, transition };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={[
        'field-list-item',
        selected ? 'field-list-item--selected' : '',
        hasError ? 'field-list-item--error' : '',
        isDragging ? 'field-list-item--dragging' : '',
      ].join(' ')}
      onClick={() => onSelect(field.stableId)}
    >
      <div
        ref={setActivatorNodeRef}
        className="field-list-item__drag-handle"
        {...attributes}
        {...listeners}
        role="button"
        tabIndex={0}
        aria-label="拖拽字段排序"
      >
        <IconHandle />
      </div>
      <div className="field-list-item__main">
        <Typography.Text strong ellipsis={{ showTooltip: true }}>
          {field.label || '未命名字段'}
        </Typography.Text>
        <Tag size="small" color={hasError ? 'red' : 'blue'}>
          {SCHEMA_FIELD_TYPE_LABELS[field.type]}
        </Tag>
      </div>
      <Button
        icon={<IconDelete />}
        type="danger"
        theme="borderless"
        size="small"
        aria-label="删除字段"
        onClick={(event) => {
          event.stopPropagation();
          onDelete(field.stableId);
        }}
      />
    </div>
  );
}
