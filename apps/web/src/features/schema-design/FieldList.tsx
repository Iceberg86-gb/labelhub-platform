import { Button, Tag, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconHandle } from '@douyinfe/semi-icons';
import {
  useSortable,
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
  const moveField = (from: number, to: number) => {
    if (from === to || to < 0 || to >= fields.length) return;
    const next = [...fields];
    const [field] = next.splice(from, 1);
    next.splice(to, 0, field);
    onChange(next);
  };

  if (fields.length === 0) {
    return (
      <div className="field-list-empty">
        <Typography.Text type="tertiary">还没有字段。</Typography.Text>
      </div>
    );
  }

  return (
    <div className="field-list">
      {fields.map((field, index) => (
        <PlainFieldListItem
          key={field.stableId}
          field={field}
          selected={field.stableId === selectedStableId}
          hasError={errors.has(field.stableId)}
          isFirst={index === 0}
          isLast={index === fields.length - 1}
          onSelect={onSelect}
          onDelete={onDelete}
          onMoveUp={() => moveField(index, index - 1)}
          onMoveDown={() => moveField(index, index + 1)}
        />
      ))}
    </div>
  );
}

type PlainFieldListItemProps = SortableFieldItemProps & {
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
};

function PlainFieldListItem({
  field,
  selected,
  hasError,
  isFirst,
  isLast,
  onSelect,
  onDelete,
  onMoveUp,
  onMoveDown,
}: PlainFieldListItemProps) {
  return (
    <div
      className={[
        'field-list-item',
        'field-list-item--plain',
        selected ? 'field-list-item--selected' : '',
        hasError ? 'field-list-item--error' : '',
      ].join(' ')}
      onClick={() => onSelect(field.stableId)}
    >
      <div className="field-list-item__order-controls" aria-label="字段顺序调整">
        <Button size="small" theme="borderless" disabled={isFirst} onClick={(event) => {
          event.stopPropagation();
          onMoveUp();
        }}>
          上移
        </Button>
        <Button size="small" theme="borderless" disabled={isLast} onClick={(event) => {
          event.stopPropagation();
          onMoveDown();
        }}>
          下移
        </Button>
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
        draggable={false}
        onDragStart={(event) => event.preventDefault()}
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
