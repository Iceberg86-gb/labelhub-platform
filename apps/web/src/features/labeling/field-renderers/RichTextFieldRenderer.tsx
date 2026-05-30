import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function RichTextFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<string>) {
  return (
    <FieldFrame field={field} errors={errors} showRequiredMarker={!readOnly}>
      {readOnly ? (
        <ReadOnlyValue value={<span dangerouslySetInnerHTML={{ __html: value ?? '' }} />} />
      ) : (
        <div
          className="labelhub-rich-text-editor"
          contentEditable
          suppressContentEditableWarning
          role="textbox"
          dangerouslySetInnerHTML={{ __html: value ?? field.content ?? '' }}
          onInput={(event) => onChange(event.currentTarget.innerHTML)}
        />
      )}
    </FieldFrame>
  );
}
