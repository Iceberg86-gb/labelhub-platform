import { Card, Tag, Typography } from '@douyinfe/semi-ui';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import type { CSSProperties } from 'react';
import { useMemo, useState } from 'react';
import { findFieldByStableId } from '../../entities/schema/fieldFactory';
import type { SchemaField, SchemaFieldType } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPES, SCHEMA_FIELD_TYPE_LABELS } from '../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../entities/schema/schemaValidation';
import { AddFieldButton } from './AddFieldButton';
import {
  PALETTE_PREFIX,
  designerDropIdFromTarget,
  paletteTypeFromDesignerId,
  resolveDesignerDragEnd,
  type DesignerDropTarget,
} from './designerDragModel';
import { SortableFieldItem } from './FieldList';

type DesignerFieldBuilderProps = {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
  onAddField: (type: SchemaFieldType, parentStableId?: string, index?: number) => SchemaField | null;
  selectedStableId: string | null;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
  errors: Map<string, FieldValidationError[]>;
  validationErrorCount: number;
};

export function DesignerFieldBuilder({
  fields,
  onChange,
  onAddField,
  selectedStableId,
  onSelect,
  onDelete,
  errors,
  validationErrorCount,
}: DesignerFieldBuilderProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const [activeDragId, setActiveDragId] = useState<string | null>(null);
  const activePaletteType = paletteTypeFromDesignerId(activeDragId);
  const activeField = useMemo(() => findFieldByStableId(fields, activeDragId), [activeDragId, fields]);

  const handleDragStart = ({ active }: DragStartEvent) => {
    setActiveDragId(String(active.id));
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    const activeId = String(active.id);
    const overId = over?.id ? String(over.id) : null;
    setActiveDragId(null);

    const resolution = resolveDesignerDragEnd(fields, { activeId, overId });
    if (resolution.kind === 'add') {
      onAddField(resolution.fieldType, resolution.parentStableId, resolution.index);
      return;
    }
    if (resolution.kind === 'change') {
      onChange(resolution.fields);
    }
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={() => setActiveDragId(null)}>
      <Card className="schema-designer-panel schema-material-panel">
        <div className="schema-designer-panel__header">
          <div>
            <Typography.Title heading={5}>物料区</Typography.Title>
            <Typography.Text type="tertiary">拖入画布创建字段。</Typography.Text>
          </div>
        </div>
        <FieldTypePalette />
      </Card>

      <Card className="schema-designer-panel schema-canvas-panel">
        <div className="schema-designer-panel__header">
          <div>
            <Typography.Title heading={5}>画布</Typography.Title>
            <Typography.Text type="tertiary">拖入物料创建字段，也可拖拽字段排序。</Typography.Text>
          </div>
          <AddFieldButton onPick={(type) => onAddField(type)} />
        </div>
        {validationErrorCount > 0 ? (
          <div className="schema-validation-summary">
            <Typography.Text strong>当前有 {validationErrorCount} 条校验问题。</Typography.Text>
          </div>
        ) : null}
        <CanvasFieldList
          fields={fields}
          target={{ kind: 'root' }}
          selectedStableId={selectedStableId}
          onSelect={onSelect}
          onDelete={onDelete}
          errors={errors}
        />
      </Card>

      <DragOverlay>
        {activePaletteType ? <PaletteDragPreview type={activePaletteType} /> : null}
        {activeField ? <FieldDragPreview field={activeField} hasError={errors.has(activeField.stableId)} /> : null}
      </DragOverlay>
    </DndContext>
  );
}

function FieldTypePalette() {
  return (
    <div className="field-type-palette">
      {SCHEMA_FIELD_TYPES.map((type) => (
        <PaletteItem key={type} type={type} />
      ))}
    </div>
  );
}

function PaletteItem({ type }: { type: SchemaFieldType }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `${PALETTE_PREFIX}${type}`,
  });
  const style: CSSProperties = transform
    ? { transform: `translate3d(${Math.round(transform.x)}px, ${Math.round(transform.y)}px, 0)` }
    : {};

  return (
    <button
      ref={setNodeRef}
      type="button"
      className={['field-type-palette__item', isDragging ? 'field-type-palette__item--dragging' : ''].join(' ')}
      style={style}
      {...attributes}
      {...listeners}
    >
      <Tag color="blue" size="small">
        {SCHEMA_FIELD_TYPE_LABELS[type]}
      </Tag>
    </button>
  );
}

function CanvasFieldList({
  fields,
  target,
  selectedStableId,
  onSelect,
  onDelete,
  errors,
}: Pick<DesignerFieldBuilderProps, 'fields' | 'selectedStableId' | 'onSelect' | 'onDelete' | 'errors'> & { target: DesignerDropTarget }) {
  const { isOver, setNodeRef } = useDroppable({ id: designerDropIdFromTarget(target) });
  const isRoot = target.kind === 'root';
  const emptyText = isRoot ? '从左侧拖入物料创建第一个字段。' : '拖入字段到此处。';

  return (
    <div
      ref={setNodeRef}
      className={[
        'schema-canvas-dropzone',
        isRoot ? 'schema-canvas-dropzone--root' : 'schema-canvas-dropzone--nested',
        isOver ? 'schema-canvas-dropzone--over' : '',
      ].join(' ')}
      data-drop-id={designerDropIdFromTarget(target)}
    >
      {fields.length === 0 ? (
        <div className="field-list-empty">
          <Typography.Text type="tertiary">{emptyText}</Typography.Text>
        </div>
      ) : (
        <SortableContext items={fields.map((field) => field.stableId)} strategy={verticalListSortingStrategy}>
          <div className="field-list">
            {fields.map((field) => (
              <div key={field.stableId} className="field-tree-node">
                <SortableFieldItem
                  field={field}
                  selected={field.stableId === selectedStableId}
                  hasError={errors.has(field.stableId)}
                  onSelect={onSelect}
                  onDelete={onDelete}
                />
                <CanvasFieldChildren
                  field={field}
                  selectedStableId={selectedStableId}
                  onSelect={onSelect}
                  onDelete={onDelete}
                  errors={errors}
                />
              </div>
            ))}
          </div>
        </SortableContext>
      )}
    </div>
  );
}

function CanvasFieldChildren({
  field,
  selectedStableId,
  onSelect,
  onDelete,
  errors,
}: Pick<DesignerFieldBuilderProps, 'selectedStableId' | 'onSelect' | 'onDelete' | 'errors'> & { field: SchemaField }) {
  if (field.type === 'nested_object') {
    return (
      <div className="schema-canvas-child-container">
        <Typography.Text type="tertiary">子字段</Typography.Text>
        <CanvasFieldList
          fields={field.children ?? []}
          target={{ kind: 'nested', stableId: field.stableId }}
          selectedStableId={selectedStableId}
          onSelect={onSelect}
          onDelete={onDelete}
          errors={errors}
        />
      </div>
    );
  }

  if (field.type === 'tab_container') {
    return (
      <div className="schema-canvas-child-container schema-canvas-tab-container">
        {(field.tabs ?? []).map((tab) => (
          <div key={tab.stableId} className="schema-canvas-tab-pane">
            <div className="schema-canvas-tab-pane__label">
              <Typography.Text strong>{tab.label || '未命名 Tab'}</Typography.Text>
            </div>
            <CanvasFieldList
              fields={tab.children ?? []}
              target={{ kind: 'tab', containerStableId: field.stableId, tabStableId: tab.stableId }}
              selectedStableId={selectedStableId}
              onSelect={onSelect}
              onDelete={onDelete}
              errors={errors}
            />
          </div>
        ))}
      </div>
    );
  }

  return null;
}

function PaletteDragPreview({ type }: { type: SchemaFieldType }) {
  return (
    <div className="field-drag-preview">
      <Tag color="blue">{SCHEMA_FIELD_TYPE_LABELS[type]}</Tag>
    </div>
  );
}

function FieldDragPreview({ field, hasError }: { field: SchemaField; hasError: boolean }) {
  return (
    <div className={['field-drag-preview', hasError ? 'field-drag-preview--error' : ''].join(' ')}>
      <strong>{field.label || '未命名字段'}</strong>
      <Tag color={hasError ? 'red' : 'blue'}>{SCHEMA_FIELD_TYPE_LABELS[field.type]}</Tag>
    </div>
  );
}
