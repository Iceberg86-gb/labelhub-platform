import { createForm, onFormValuesChange, type Form } from '@formily/core';
import { createSchemaField, FormProvider } from '@formily/react';
import { useEffect, useMemo } from 'react';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import { answerPayloadToFormilyValues } from './adapters/answerPayloadToFormilyValues';
import { formilyValuesToAnswerPayload } from './adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from './adapters/schemaToFormilyISchema';
import { componentsMap } from './components';

const FormilySchemaField = createSchemaField({ components: componentsMap });

export interface SchemaFormilyRendererProps {
  schemaFields: SchemaField[];
  value?: AnswerPayload | null;
  onChange: (value: AnswerPayload) => void;
  readOnly?: boolean;
  errors?: Map<string, string[]>;
}

export function SchemaFormilyRenderer({
  schemaFields,
  value,
  onChange,
  readOnly = false,
  errors,
}: SchemaFormilyRendererProps) {
  const form = useMemo(
    () => createSchemaFormilyForm({ schemaFields, value, onChange, readOnly }),
    [schemaFields, value, onChange, readOnly],
  );
  const schema = useMemo(() => schemaToFormilyISchema(schemaFields), [schemaFields]);

  useEffect(() => {
    applyExternalErrorsToForm(form, schemaFields, errors);
  }, [errors, form, schemaFields]);

  if (!schemaFields.length) {
    return <div className="schema-renderer schema-renderer--empty">暂无字段。</div>;
  }

  return (
    <div className="schema-renderer schema-renderer--formily">
      <FormProvider form={form}>
        <FormilySchemaField schema={schema} />
      </FormProvider>
    </div>
  );
}

export function createSchemaFormilyForm({
  schemaFields,
  value,
  onChange,
  readOnly = false,
}: SchemaFormilyRendererProps): Form<Record<string, unknown>> {
  return createForm<Record<string, unknown>>({
    initialValues: answerPayloadToFormilyValues(value ?? {}),
    readPretty: readOnly,
    effects() {
      onFormValuesChange((form) => {
        onChange(formilyValuesToAnswerPayload(form.values, schemaFields));
      });
    },
  });
}

export function applyExternalErrorsToForm(
  form: Form<Record<string, unknown>>,
  schemaFields: SchemaField[],
  errors?: Map<string, string[]>,
) {
  for (const { path, field } of flattenFieldPaths(schemaFields)) {
    const fieldErrors = errors?.get(path) ?? errors?.get(field.stableId) ?? [];
    form.setFieldState(path, (state) => {
      state.selfErrors = fieldErrors;
    });
  }
}

function flattenFieldPaths(fields: SchemaField[], parentPath?: string): Array<{ path: string; field: SchemaField }> {
  return fields.flatMap((field) => {
    const path = parentPath ? `${parentPath}.${field.stableId}` : field.stableId;
    const current = { path, field };
    if (field.type !== 'nested_object') {
      return [current];
    }
    return [current, ...flattenFieldPaths(field.children ?? [], path)];
  });
}
