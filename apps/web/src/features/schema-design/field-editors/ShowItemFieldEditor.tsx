import { AutoComplete, Input, TextArea, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors } from './editorUtils';

type SourcePathAutoCompleteItem = {
  value: string;
  label: string;
  typeLabel: string;
};

export function ShowItemFieldEditor({ field, onChange, errors, sourcePathOptions = [] }: FieldEditorProps) {
  const optionItems: SourcePathAutoCompleteItem[] = sourcePathOptions.map((option) => ({
    value: option.value,
    label: `${option.value} ${option.typeLabel}`,
    typeLabel: option.typeLabel,
  }));
  const currentSourcePath = field.sourcePath ?? '';
  const hasSourcePathMismatch = currentSourcePath.trim() !== ''
    && sourcePathOptions.length > 0
    && !sourcePathOptions.some((option) => option.value === currentSourcePath);

  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="展示项">
        <label className="field-editor-row">
          <Typography.Text>标题</Typography.Text>
          <Input value={field.label} validateStatus={errors.length ? 'error' : 'default'} onChange={(label) => onChange({ ...field, label })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>展示内容</Typography.Text>
          <TextArea autosize value={field.content} placeholder="给标注员展示的说明、样例或只读上下文" onChange={(content) => onChange({ ...field, content })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>源数据路径</Typography.Text>
          <AutoComplete<SourcePathAutoCompleteItem>
            aria-label="源数据路径"
            value={currentSourcePath}
            data={optionItems}
            placeholder="例如 prompt、question.text、items.0.title"
            emptyContent="无匹配字段,可继续手输"
            onChange={(sourcePath) => onChange({ ...field, sourcePath: String(sourcePath) })}
            onSelect={(option) => onChange({ ...field, sourcePath: option.value })}
            renderItem={(option) => (
              <div className="source-path-option">
                <Typography.Text>{option.value}</Typography.Text>
                <Typography.Text type="tertiary">{option.typeLabel}</Typography.Text>
              </div>
            )}
          />
          {hasSourcePathMismatch ? (
            <Typography.Text type="warning" size="small">未在数据集字段中找到,请确认拼写</Typography.Text>
          ) : null}
        </label>
      </EditorSection>
    </div>
  );
}
