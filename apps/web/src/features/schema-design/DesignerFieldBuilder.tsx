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
  arrayMove,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import type { CSSProperties } from 'react';
import { useMemo, useState } from 'react';
import type { SchemaField, SchemaFieldType } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPES, SCHEMA_FIELD_TYPE_LABELS } from '../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../entities/schema/schemaValidation';
import { AddFieldButton } from './AddFieldButton';
import { SortableFieldItem } from './FieldList';

const CANVAS_DROP_ID = 'schema-designer-canvas-dropzone';
const PALETTE_PREFIX = 'palette:';

type DesignerFieldBuilderProps = {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
  onAddField: (type: SchemaFieldType, index?: number) => SchemaField | null;
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
  const activePaletteType = paletteTypeFromId(activeDragId);
  const activeField = useMemo(() => fields.find((field) => field.stableId === activeDragId) ?? null, [activeDragId, fields]);

  const handleDragStart = ({ active }: DragStartEvent) => {
    setActiveDragId(String(active.id));
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    const activeId = String(active.id);
    const overId = over?.id ? String(over.id) : CANVAS_DROP_ID;
    setActiveDragId(null);

    const paletteType = paletteTypeFromId(activeId);
    if (paletteType) {
      const insertIndex = fields.findIndex((field) => field.stableId === overId);
      onAddField(paletteType, insertIndex >= 0 ? insertIndex : fields.length);
      return;
    }

    if (!over || active.id === over.id) return;
    const oldIndex = fields.findIndex((field) => field.stableId === active.id);
    const newIndex = fields.findIndex((field) => field.stableId === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    onChange(arrayMove(fields, oldIndex, newIndex));
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
  selectedStableId,
  onSelect,
  onDelete,
  errors,
}: Pick<DesignerFieldBuilderProps, 'fields' | 'selectedStableId' | 'onSelect' | 'onDelete' | 'errors'>) {
  const { isOver, setNodeRef } = useDroppable({ id: CANVAS_DROP_ID });

  return (
    <div ref={setNodeRef} className={['schema-canvas-dropzone', isOver ? 'schema-canvas-dropzone--over' : ''].join(' ')}>
      {fields.length === 0 ? (
        <div className="field-list-empty">
          <Typography.Text type="tertiary">从左侧拖入物料创建第一个字段。</Typography.Text>
        </div>
      ) : (
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
      )}
    </div>
  );
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

function paletteTypeFromId(id: string | null): SchemaFieldType | null {
  if (!id?.startsWith(PALETTE_PREFIX)) return null;
  const type = id.slice(PALETTE_PREFIX.length);
  return SCHEMA_FIELD_TYPES.includes(type as SchemaFieldType) ? type as SchemaFieldType : null;
}
