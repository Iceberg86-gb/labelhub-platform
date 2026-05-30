import { Input, Switch, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { CustomValidationSection, EditorSection, FieldErrors, updateValidation } from './editorUtils';

export function JsonFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="JSON 字段">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input value={field.label} validateStatus={errors.length ? 'error' : 'default'} onChange={(label) => onChange({ ...field, label })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>帮助说明</Typography.Text>
          <Input value={field.help} placeholder="给标注员看的简短说明" onChange={(help) => onChange({ ...field, help })} />
        </label>
        <div className="field-editor-toggle">
          <Typography.Text>必填</Typography.Text>
          <Switch checked={Boolean(field.validation?.required)} onChange={(required) => onChange(updateValidation(field, { required }))} />
        </div>
      </EditorSection>
      <CustomValidationSection field={field} onChange={onChange} />
    </div>
  );
}
