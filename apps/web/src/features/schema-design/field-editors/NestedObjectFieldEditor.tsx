import { Input, Typography } from '@douyinfe/semi-ui';
import { createField } from '../../../entities/schema/fieldFactory';
import type { SchemaFieldType } from '../../../entities/schema/schemaTypes';
import { AddFieldButton } from '../AddFieldButton';
import { FieldList } from '../FieldList';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors } from './editorUtils';

export function NestedObjectFieldEditor({
  field,
  onChange,
  errors,
  errorsByField,
  selectedStableId,
  onSelect,
  onDelete,
}: FieldEditorProps) {
  const children = field.children ?? [];

  const handleAddChild = (type: SchemaFieldType) => {
    const child = createField(type);
    onChange({ ...field, children: [...children, child] });
    onSelect(child.stableId);
  };

  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="嵌套对象">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：地址信息"
            onChange={(label) => onChange({ ...field, label })}
          />
        </label>
        <Typography.Text type="tertiary">嵌套对象用于组织一组子字段。P4b 仅支持一层 children，不支持 children 内再添加嵌套对象。</Typography.Text>
      </EditorSection>
      <EditorSection title="子字段">
        <div className="nested-children-panel">
          <AddFieldButton label="添加子字段" excludeTypes={['nested_object', 'tab_container']} onPick={handleAddChild} />
          <FieldList
            fields={children}
            onChange={(nextChildren) => onChange({ ...field, children: nextChildren })}
            selectedStableId={selectedStableId}
            onSelect={onSelect}
            onDelete={onDelete}
            errors={errorsByField}
          />
        </div>
      </EditorSection>
    </div>
  );
}
