import { Typography } from '@douyinfe/semi-ui';
import { useEffect, useState } from 'react';
import type { LinkageCondition, SchemaField } from '../../../entities/schema/schemaTypes';
import { EditorSection } from './editorUtils';

type LinkageKey = 'visibleWhen' | 'requiredWhen';

type ParseResult = { ok: true; value: LinkageCondition | undefined } | { ok: false; reason: string };

type PatchResult = { ok: true; field: SchemaField } | { ok: false; reason: string };

const JSON_ERROR = 'JSON 格式错误';

export function formatLinkageConditionForEditor(condition: LinkageCondition | undefined): string {
  return condition ? JSON.stringify(condition, null, 2) : '';
}

export function parseLinkageConditionInput(input: string): ParseResult {
  if (input.trim() === '') {
    return { ok: true, value: undefined };
  }

  try {
    return { ok: true, value: JSON.parse(input) as LinkageCondition };
  } catch {
    return { ok: false, reason: JSON_ERROR };
  }
}

export function applyLinkageConditionPatch(field: SchemaField, key: LinkageKey, input: string): PatchResult {
  const parsed = parseLinkageConditionInput(input);
  if (!parsed.ok) return parsed;

  return {
    ok: true,
    field: {
      ...field,
      [key]: parsed.value,
    },
  };
}

type LinkageJsonEditorProps = {
  field: SchemaField;
  onChange: (field: SchemaField) => void;
};

export function LinkageJsonEditor({ field, onChange }: LinkageJsonEditorProps) {
  const formattedVisibleWhen = formatLinkageConditionForEditor(field.visibleWhen);
  const formattedRequiredWhen = formatLinkageConditionForEditor(field.requiredWhen);
  const [drafts, setDrafts] = useState<Record<LinkageKey, string>>({
    visibleWhen: formattedVisibleWhen,
    requiredWhen: formattedRequiredWhen,
  });
  const [errors, setErrors] = useState<Partial<Record<LinkageKey, string>>>({});

  useEffect(() => {
    setDrafts({
      visibleWhen: formattedVisibleWhen,
      requiredWhen: formattedRequiredWhen,
    });
    setErrors({});
  }, [field.stableId, formattedVisibleWhen, formattedRequiredWhen]);

  const handleChange = (key: LinkageKey, input: string) => {
    setDrafts((current) => ({ ...current, [key]: input }));

    const result = applyLinkageConditionPatch(field, key, input);
    if (!result.ok) {
      setErrors((current) => ({ ...current, [key]: result.reason }));
      return;
    }

    setErrors((current) => ({ ...current, [key]: undefined }));
    onChange(result.field);
  };

  return (
    <EditorSection title="高级联动 JSON">
      <Typography.Text type="tertiary" className="field-linkage-json-help">
        仅检查 JSON 语法。字段引用、操作符和值类型会在发布时统一校验。
      </Typography.Text>
      <LinkageJsonTextarea
        label="visibleWhen"
        value={drafts.visibleWhen}
        error={errors.visibleWhen}
        placeholder={'{"field":"type","op":"eq","value":"other"}'}
        onChange={(input) => handleChange('visibleWhen', input)}
      />
      <LinkageJsonTextarea
        label="requiredWhen"
        value={drafts.requiredWhen}
        error={errors.requiredWhen}
        placeholder={'{"field":"type","op":"eq","value":"other"}'}
        onChange={(input) => handleChange('requiredWhen', input)}
      />
    </EditorSection>
  );
}

type LinkageJsonTextareaProps = {
  label: LinkageKey;
  value: string;
  error?: string;
  placeholder: string;
  onChange: (value: string) => void;
};

function LinkageJsonTextarea({ label, value, error, placeholder, onChange }: LinkageJsonTextareaProps) {
  return (
    <label className="field-editor-row">
      <Typography.Text>{label}</Typography.Text>
      <textarea
        className="field-linkage-json-textarea"
        value={value}
        placeholder={placeholder}
        spellCheck={false}
        aria-invalid={Boolean(error)}
        onChange={(event) => onChange(event.currentTarget.value)}
      />
      {error ? (
        <Typography.Text className="field-linkage-json-error" role="alert">
          {error}
        </Typography.Text>
      ) : null}
    </label>
  );
}
