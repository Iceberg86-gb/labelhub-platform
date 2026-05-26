import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import { coerceAnswerPayload } from '../../../entities/submission/answerPayload';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame } from './rendererUtils';
import { SchemaRenderer } from '../SchemaRenderer';

export function NestedObjectFieldRenderer({
  field,
  value,
  onChange,
  readOnly,
  errors,
}: FieldRendererProps<AnswerPayload>) {
  const childPayload = coerceAnswerPayload(value);

  return (
    <FieldFrame field={field} errors={errors}>
      <div className="nested-renderer">
        <SchemaRenderer
          fields={field.children ?? []}
          value={childPayload}
          onChange={onChange}
          readOnly={readOnly}
        />
      </div>
    </FieldFrame>
  );
}
