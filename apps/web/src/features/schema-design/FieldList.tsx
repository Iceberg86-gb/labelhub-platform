import { Button, Popconfirm, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconCopy, IconDelete, IconHandle } from '@douyinfe/semi-icons';
import { StatusBadge } from '../../shared/ui';
import {
  useSortable,
} from '@dnd-kit/sortable';
import type { CSSProperties, FocusEvent } from 'react';
import { useState } from 'react';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPE_LABELS } from '../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../entities/schema/schemaValidation';
import { isContainerField } from '../../entities/schema/fieldFactory';

type FieldListProps = {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
  selectedStableId: string | null;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
  onDuplicate: (stableId: string) => void;
  errors: Map<string, FieldValidationError[]>;
};

export function FieldList({ fields, onChange, selectedStableId, onSelect, onDelete, onDuplicate, errors }: FieldListProps) {
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
          onDuplicate={onDuplicate}
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
  onDuplicate,
  onMoveUp,
  onMoveDown,
}: PlainFieldListItemProps) {
  const [showDeleteAction, setShowDeleteAction] = useState(false);

  const hideDeleteAction = (event: FocusEvent<HTMLDivElement>) => {
    if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
    setShowDeleteAction(false);
  };

  return (
    <div
      className={[
        'field-list-item',
        'field-list-item--plain',
        selected ? 'field-list-item--selected' : '',
        hasError ? 'field-list-item--error' : '',
      ].join(' ')}
      onClick={() => onSelect(field.stableId)}
      onMouseEnter={() => setShowDeleteAction(true)}
      onMouseLeave={() => setShowDeleteAction(false)}
      onFocus={() => setShowDeleteAction(true)}
      onBlur={hideDeleteAction}
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
        <StatusBadge tone={hasError ? 'danger' : 'accent'} size="small">
          {SCHEMA_FIELD_TYPE_LABELS[field.type]}
        </StatusBadge>
      </div>
      <FieldActions
        visible={showDeleteAction}
        field={field}
        onDuplicate={() => onDuplicate(field.stableId)}
        onDelete={() => onDelete(field.stableId)}
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
  onDuplicate: (stableId: string) => void;
};

export function SortableFieldItem({ field, selected, hasError, onSelect, onDelete, onDuplicate }: SortableFieldItemProps) {
  const { attributes, listeners, setActivatorNodeRef, setNodeRef, transform, transition, isDragging } = useSortable({ id: field.stableId });
  const [showDeleteAction, setShowDeleteAction] = useState(false);
  const transformStyle = transform
    ? `translate3d(${Math.round(transform.x)}px, ${Math.round(transform.y)}px, 0) scaleX(${transform.scaleX}) scaleY(${transform.scaleY})`
    : undefined;
  const style: CSSProperties = { transform: transformStyle, transition };
  const hideDeleteAction = (event: FocusEvent<HTMLDivElement>) => {
    if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
    setShowDeleteAction(false);
  };

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
      onMouseEnter={() => setShowDeleteAction(true)}
      onMouseLeave={() => setShowDeleteAction(false)}
      onFocus={() => setShowDeleteAction(true)}
      onBlur={hideDeleteAction}
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
        <StatusBadge tone={hasError ? 'danger' : 'accent'} size="small">
          {SCHEMA_FIELD_TYPE_LABELS[field.type]}
        </StatusBadge>
      </div>
      <FieldActions
        visible={showDeleteAction}
        field={field}
        onDuplicate={() => onDuplicate(field.stableId)}
        onDelete={() => onDelete(field.stableId)}
      />
    </div>
  );
}

function FieldActions({
  visible,
  field,
  onDuplicate,
  onDelete,
}: {
  visible: boolean;
  field: SchemaField;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const canDuplicate = !isContainerField(field);

  return (
    <span
      className={[
        'field-list-item__actions',
        'field-list-item__delete-action',
        'field-list-item__delete-action--deferred',
        visible ? 'field-list-item__delete-action--visible' : '',
      ].join(' ')}
      style={fieldDeleteActionStyle(visible)}
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => event.stopPropagation()}
    >
      <Tooltip content={canDuplicate ? '复制字段' : '暂不支持复制容器字段'}>
        <Button
          icon={<IconCopy />}
          theme="borderless"
          size="small"
          disabled={!canDuplicate}
          aria-label="复制字段"
          onClick={canDuplicate ? onDuplicate : undefined}
        />
      </Tooltip>
      <Popconfirm
        title="删除字段?"
        content={field.label ? `确认删除「${field.label}」字段?` : '确认删除该字段?'}
        okText="删除"
        cancelText="取消"
        position="leftTop"
        autoAdjustOverflow
        getPopupContainer={() => document.body}
        onConfirm={onDelete}
      >
        <Button
          icon={<IconDelete />}
          type="danger"
          theme="borderless"
          size="small"
          aria-label="删除字段"
        />
      </Popconfirm>
    </span>
  );
}

function fieldDeleteActionStyle(visible: boolean): CSSProperties {
  return {
    position: 'relative',
    zIndex: 1,
    opacity: visible ? 1 : 0,
    pointerEvents: visible ? 'auto' : 'none',
    transform: visible ? 'none' : 'translateX(4px)',
    transition: 'opacity 120ms ease, transform 120ms ease',
  };
}
