import { TextArea } from '@douyinfe/semi-ui';
import type { AnswerValue } from '../../../entities/submission/answerPayload';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function JsonFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<AnswerValue>) {
  const text = value == null ? '' : JSON.stringify(value, null, 2);
  return (
    <FieldFrame field={field} errors={errors} showRequiredMarker={!readOnly}>
      {readOnly ? (
        <ReadOnlyValue value={text} />
      ) : (
        <TextArea
          autosize
          value={text}
          placeholder={field.placeholder ?? '{ }'}
          onChange={(next) => {
            if (!next.trim()) return onChange(null);
            try {
              onChange(JSON.parse(next) as AnswerValue);
            } catch {
              return;
            }
          }}
        />
      )}
    </FieldFrame>
  );
}
