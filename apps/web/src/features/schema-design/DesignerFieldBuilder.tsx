import { Card, Tag, Typography } from '@douyinfe/semi-ui';
import {
  IconAIWandLevel1,
  IconArticle,
  IconBox,
  IconCalendar,
  IconCheckboxTick,
  IconCode,
  IconEyeOpened,
  IconHash,
  IconRadio,
  IconTabsStroked,
  IconText,
  IconUpload,
} from '@douyinfe/semi-icons';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
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
import { useCallback, useMemo, useState } from 'react';
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

export const FIELD_TYPE_PALETTE_GROUPS: Array<{ title: string; types: SchemaFieldType[] }> = [
  { title: '只读材料', types: ['show_item'] },
  { title: '选择与约束', types: ['single_select', 'multi_select', 'date'] },
  { title: '内容录入', types: ['text', 'number', 'rich_text', 'file_upload'] },
  { title: '容器与高级组件', types: ['nested_object', 'tab_container', 'json_editor', 'llm_interaction'] },
];

const FIELD_TYPE_PALETTE_ICONS: Record<SchemaFieldType, JSX.Element> = {
  show_item: <IconEyeOpened />,
  single_select: <IconRadio />,
  multi_select: <IconCheckboxTick />,
  date: <IconCalendar />,
  text: <IconText />,
  number: <IconHash />,
  rich_text: <IconArticle />,
  file_upload: <IconUpload />,
  nested_object: <IconBox />,
  tab_container: <IconTabsStroked />,
  json_editor: <IconCode />,
  llm_interaction: <IconAIWandLevel1 />,
};

export function groupPaletteTypes(types: readonly SchemaFieldType[] = SCHEMA_FIELD_TYPES) {
  const knownTypes = new Set(types);
  const groupedTypes = new Set(FIELD_TYPE_PALETTE_GROUPS.flatMap((group) => group.types));
  const groups = FIELD_TYPE_PALETTE_GROUPS
    .map((group) => ({
      title: group.title,
      types: group.types.filter((type) => knownTypes.has(type)),
    }))
    .filter((group) => group.types.length > 0);
  const ungroupedTypes = types.filter((type) => !groupedTypes.has(type));
  return ungroupedTypes.length > 0 ? [...groups, { title: '其他', types: ungroupedTypes }] : groups;
}

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
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 120, tolerance: 6 } }),
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

  const handleDragCancel = () => {
    setActiveDragId(null);
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <Card className="schema-designer-panel schema-material-panel schema-material-panel--rail">
        <div className="schema-designer-panel__header">
          <div>
            <Typography.Title heading={5}>物料区</Typography.Title>
            <Typography.Text type="tertiary">拖入画布创建字段。</Typography.Text>
          </div>
        </div>
        <div className="field-type-palette__drag-hint">
          <Typography.Text type="tertiary">按住物料拖到中间画布，蓝色落点出现后松开。</Typography.Text>
        </div>
        <FieldTypePalette />
      </Card>

      <Card className="schema-designer-panel schema-canvas-panel schema-canvas-panel--workspace">
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
  const groups = groupPaletteTypes();
  return (
    <div className="field-type-palette">
      {groups.map((group) => (
        <div key={group.title} className="field-type-palette__group">
          <Typography.Text className="field-type-palette__group-title" strong>
            {group.title}
          </Typography.Text>
          <div className="field-type-palette__group-items">
            {group.types.map((type) => (
              <PaletteItem key={type} type={type} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function PaletteItem({ type }: { type: SchemaFieldType }) {
  const { attributes, listeners, setActivatorNodeRef, setNodeRef, isDragging } = useDraggable({
    id: `${PALETTE_PREFIX}${type}`,
  });
  const setPaletteRef = useCallback(
    (node: HTMLDivElement | null) => {
      setNodeRef(node);
      setActivatorNodeRef(node);
    },
    [setActivatorNodeRef, setNodeRef],
  );

  return (
    <div
      ref={setPaletteRef}
      className={['field-type-palette__item', isDragging ? 'field-type-palette__item--dragging' : ''].join(' ')}
      {...attributes}
      {...listeners}
      draggable={false}
      onDragStart={(event) => event.preventDefault()}
      role="button"
      tabIndex={0}
    >
      <span className="field-type-palette__icon" aria-hidden>
        {FIELD_TYPE_PALETTE_ICONS[type]}
      </span>
      <Tag className="semantic-tag semantic-tag--accent" size="small">
        {SCHEMA_FIELD_TYPE_LABELS[type]}
      </Tag>
    </div>
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
      <Tag className="semantic-tag semantic-tag--accent">{SCHEMA_FIELD_TYPE_LABELS[type]}</Tag>
    </div>
  );
}

function FieldDragPreview({ field, hasError }: { field: SchemaField; hasError: boolean }) {
  return (
    <div className={['field-drag-preview', hasError ? 'field-drag-preview--error' : ''].join(' ')}>
      <strong>{field.label || '未命名字段'}</strong>
      <Tag className={`semantic-tag semantic-tag--${hasError ? 'danger' : 'accent'}`}>{SCHEMA_FIELD_TYPE_LABELS[field.type]}</Tag>
    </div>
  );
}
