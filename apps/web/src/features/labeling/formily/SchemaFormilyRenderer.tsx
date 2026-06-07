import { createForm, onFormValuesChange, type Form } from '@formily/core';
import { createSchemaField, FormProvider, type ISchema } from '@formily/react';
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { buildFlatValueIndex, isFieldConditionallyRequired, isFieldVisible } from '../../../entities/labeling/linkageEvaluator';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import { answerPayloadToFormilyValues } from './adapters/answerPayloadToFormilyValues';
import { formilyValuesToAnswerPayload } from './adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from './adapters/schemaToFormilyISchema';
import { componentsMap } from './components';

const FormilySchemaField = createSchemaField({ components: componentsMap });
export const FORMILY_VIRTUALIZATION_THRESHOLD = 50;
const VIRTUAL_SCROLL_HEIGHT = 720;
const VIRTUAL_SCROLL_FALLBACK_RECT = { width: 800, height: VIRTUAL_SCROLL_HEIGHT };

export interface SchemaFormilyRendererProps {
  schemaFields: SchemaField[];
  value?: AnswerPayload | null;
  onChange: (value: AnswerPayload) => void;
  readOnly?: boolean;
  errors?: Map<string, string[]>;
  onFormReady?: (form: Form<Record<string, unknown>>) => void;
  sessionId?: number;
  itemPayload?: unknown;
}

export function SchemaFormilyRenderer({
  schemaFields,
  value,
  onChange,
  readOnly = false,
  errors,
  onFormReady,
  sessionId,
  itemPayload,
}: SchemaFormilyRendererProps) {
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const form = useMemo(
    () => createSchemaFormilyForm({ schemaFields, value, onChange: (next) => onChangeRef.current(next), readOnly }),
    [schemaFields, readOnly, sessionId],
  );
  const schema = useMemo(() => schemaToFormilyISchema(schemaFields, { sessionId, itemPayload }), [schemaFields, sessionId, itemPayload]);

  useEffect(() => {
    onFormReady?.(form);
  }, [form, onFormReady]);

  useEffect(() => {
    applyLinkageStateToForm(form, schemaFields);
  }, [form, schemaFields]);

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
    initialValues: answerPayloadToFormilyValues(value ?? {}, schemaFields),
    readPretty: readOnly,
    effects() {
      onFormValuesChange((form) => {
        applyLinkageStateToForm(form, schemaFields);
        onChange(formilyValuesToAnswerPayload(form.values, schemaFields));
      });
    },
  });
}

export function applyLinkageStateToForm(form: Form<Record<string, unknown>>, schemaFields: SchemaField[]) {
  const values = formilyValuesToAnswerPayload(form.values, schemaFields);
  const flatValues = buildFlatValueIndex(schemaFields, values);

  for (const { path, field } of flattenFieldPaths(schemaFields)) {
    if (!field.visibleWhen && !field.requiredWhen) {
      continue;
    }

    form.setFieldState(path, (state) => {
      const visible = isFieldVisible(field, flatValues);
      state.visible = visible;
      state.display = visible ? 'visible' : 'none';
      state.required = visible && ((field.validation?.required ?? false) || isFieldConditionallyRequired(field, flatValues));
    });
  }
}

function VirtualizedFormilyFields({ schema, schemaFields }: { schema: ISchema; schemaFields: SchemaField[] }) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const initialRect = useMeasuredVirtualScrollRect(scrollRef);
  const virtualizer = useVirtualizer({
    count: schemaFields.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 112,
    overscan: 6,
    initialRect,
  });
  const properties = (schema.properties ?? {}) as Record<string, ISchema>;

  return (
    <div
      ref={scrollRef}
      className="schema-formily-virtual-scroll"
      style={{ maxHeight: VIRTUAL_SCROLL_HEIGHT, overflow: 'auto', contain: 'strict' }}
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

function useMeasuredVirtualScrollRect(scrollRef: RefObject<HTMLElement>) {
  const [rect, setRect] = useState(VIRTUAL_SCROLL_FALLBACK_RECT);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element) {
      return undefined;
    }

    const measure = () => {
      const measuredWidth = Math.round(element.getBoundingClientRect().width);
      setRect((current) => {
        const width = measuredWidth > 0 ? measuredWidth : current.width || VIRTUAL_SCROLL_FALLBACK_RECT.width;
        if (current.width === width && current.height === VIRTUAL_SCROLL_HEIGHT) {
          return current;
        }
        return { width, height: VIRTUAL_SCROLL_HEIGHT };
      });
    };

    measure();

    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }

    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [scrollRef]);

  return rect;
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
    if (field.type === 'tab_container') {
      return [
        current,
        ...(field.tabs?.flatMap((tab) => flattenFieldPaths(tab.children ?? [], `${path}.${tab.stableId}`)) ?? []),
      ];
    }
    if (field.type !== 'nested_object') {
      return [current];
    }
    return [current, ...flattenFieldPaths(field.children ?? [], path)];
  });
}
