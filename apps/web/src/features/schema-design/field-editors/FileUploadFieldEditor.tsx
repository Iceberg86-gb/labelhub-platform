import { Input, InputNumber, Switch, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors, numberOrUndefined, updateValidation } from './editorUtils';

export function FileUploadFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="文件上传字段">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：附件"
            onChange={(label) => onChange({ ...field, label })}
          />
        </label>
        <label className="field-editor-row">
          <Typography.Text>帮助说明</Typography.Text>
          <Input value={field.help} placeholder="给标注员看的简短说明" onChange={(help) => onChange({ ...field, help })} />
        </label>
        <div className="field-editor-toggle">
          <Typography.Text>必填</Typography.Text>
          <Switch checked={Boolean(field.validation?.required)} onChange={(required) => onChange(updateValidation(field, { required }))} />
        </div>
        <label className="field-editor-row">
          <Typography.Text>允许类型</Typography.Text>
          <Input
            value={field.acceptedFileTypes?.join(',') ?? ''}
            placeholder=".png,.jpg,application/pdf"
            onChange={(value) => onChange({ ...field, acceptedFileTypes: splitList(value) })}
          />
        </label>
        <label className="field-editor-row">
          <Typography.Text>最大 MB</Typography.Text>
          <InputNumber
            min={1}
            value={field.maxFileSizeMb}
            onChange={(value) => onChange({ ...field, maxFileSizeMb: numberOrUndefined(value) })}
          />
        </label>
      </EditorSection>
    </div>
  );
}

function splitList(value: string): string[] | undefined {
  const items = value.split(',').map((item) => item.trim()).filter(Boolean);
  return items.length ? items : undefined;
}
