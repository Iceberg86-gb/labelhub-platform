import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

export function LabelHubRichTextField({ field }: { field?: SchemaField }) {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'string' ? formilyField.value : field?.content ?? '';

  if (formilyField.readPretty) {
    return <div className="labelhub-rich-text-readonly" dangerouslySetInnerHTML={{ __html: value }} />;
  }

  return (
    <div
      className="labelhub-rich-text-editor"
      contentEditable
      suppressContentEditableWarning
      role="textbox"
      aria-label={field?.label}
      data-placeholder={field?.placeholder ?? '请输入富文本'}
      dangerouslySetInnerHTML={{ __html: value }}
      onInput={(event) => formilyField.setValue(event.currentTarget.innerHTML)}
    />
  );
}
