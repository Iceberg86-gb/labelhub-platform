import { createForm, onFormValuesChange, type Form } from '@formily/core';
import { createSchemaField, FormProvider, type ISchema } from '@formily/react';
import { useEffect, useMemo, useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import { answerPayloadToFormilyValues } from './adapters/answerPayloadToFormilyValues';
import { formilyValuesToAnswerPayload } from './adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from './adapters/schemaToFormilyISchema';
import { componentsMap } from './components';

const FormilySchemaField = createSchemaField({ components: componentsMap });
export const FORMILY_VIRTUALIZATION_THRESHOLD = 50;

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
        {shouldVirtualizeSchemaFields(schemaFields.length) ? (
          <VirtualizedFormilyFields schema={schema} schemaFields={schemaFields} />
        ) : (
          <FormilySchemaField schema={schema} />
        )}
      </FormProvider>
    </div>
  );
}

export function shouldVirtualizeSchemaFields(fieldCount: number): boolean {
  return fieldCount > FORMILY_VIRTUALIZATION_THRESHOLD;
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

function VirtualizedFormilyFields({ schema, schemaFields }: { schema: ISchema; schemaFields: SchemaField[] }) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const virtualizer = useVirtualizer({
    count: schemaFields.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 112,
    overscan: 6,
    initialRect: { width: 800, height: 720 },
  });
  const properties = (schema.properties ?? {}) as Record<string, ISchema>;

  return (
    <div
      ref={scrollRef}
      className="schema-formily-virtual-scroll"
      style={{ maxHeight: 720, overflow: 'auto', contain: 'strict' }}
    >
      <div
        className="schema-formily-virtual-inner"
        style={{ height: virtualizer.getTotalSize(), position: 'relative' }}
      >
        {virtualizer.getVirtualItems().map((virtualRow) => {
          const schemaField = schemaFields[virtualRow.index];
          const fieldSchema = schemaField ? properties[schemaField.stableId] : undefined;
          if (!schemaField || !fieldSchema) {
            return null;
          }
          return (
            <div
              key={schemaField.stableId}
              ref={virtualizer.measureElement}
              data-index={virtualRow.index}
              className="schema-formily-virtual-row"
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              <FormilySchemaField schema={{ type: 'object', properties: { [schemaField.stableId]: fieldSchema } }} />
            </div>
          );
        })}
      </div>
    </div>
  );
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
