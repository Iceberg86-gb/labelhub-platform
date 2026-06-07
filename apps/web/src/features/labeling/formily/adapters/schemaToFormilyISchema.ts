import type { ISchema } from '@formily/react';
import { buildFlatValueIndex, isFieldConditionallyRequired, isFieldVisible } from '../../../../entities/labeling/linkageEvaluator';
import type { SchemaField, SchemaFieldOption, SchemaFieldValidation } from '../../../../entities/schema/schemaTypes';
import { LABEL_HUB_COMPONENTS } from './componentRegistry';
import { formilyValuesToAnswerPayload } from './formilyValuesToAnswerPayload';
import { schemaToFormilyValidators } from './schemaToFormilyValidators';

export type SchemaRuntimeContext = {
  sessionId?: number;
  itemPayload?: unknown;
  rootFields?: SchemaField[];
};

export function schemaToFormilyISchema(fields: SchemaField[], runtimeContext: SchemaRuntimeContext = {}): ISchema {
  const context = { ...runtimeContext, rootFields: runtimeContext.rootFields ?? fields };
  return {
    type: 'object',
    properties: fieldsToProperties(fields, context),
    ...(requiredFieldIds(fields).length ? { required: requiredFieldIds(fields) } : {}),
  };
}

function fieldsToProperties(fields: SchemaField[], runtimeContext: SchemaRuntimeContext): Record<string, ISchema> {
  return Object.fromEntries(fields.map((field) => [field.stableId, fieldToSchema(field, runtimeContext)]));
}

function fieldToSchema(field: SchemaField, runtimeContext: SchemaRuntimeContext): ISchema {
  const base: ISchema = {
    type: schemaType(field),
    title: field.label,
    description: field.help,
    enum: fieldOptions(field.options),
    ...validationSchema(field.validation),
    'x-validator': schemaToFormilyValidators(field),
    'x-decorator': 'FieldFrame',
    'x-decorator-props': { field },
    'x-component': LABEL_HUB_COMPONENTS[field.type],
    'x-reactions': schemaToFormilyReactions(field, runtimeContext.rootFields ?? []),
    'x-component-props': {
      field,
      placeholder: placeholderWithValidationHint(field),
      mode: field.type === 'multi_select' ? 'multiple' : undefined,
      sessionId: runtimeContext.sessionId,
      itemPayload: runtimeContext.itemPayload,
    },
  };

  if (field.type === 'show_item') {
    return removeUndefined({
      ...base,
      type: 'void',
      'x-decorator': undefined,
      'x-decorator-props': undefined,
    });
  }

  if (field.type === 'tab_container') {
    return removeUndefined({
      ...base,
      type: 'void',
      'x-decorator': undefined,
      'x-decorator-props': undefined,
      properties: Object.fromEntries((field.tabs ?? []).map((tab) => [
        tab.stableId,
        removeUndefined({
          type: 'void',
          'x-component': 'LabelHubTabPane',
          'x-component-props': { tab },
          properties: fieldsToProperties(tab.children ?? [], runtimeContext),
          ...(requiredFieldIds(tab.children ?? []).length ? { required: requiredFieldIds(tab.children ?? []) } : {}),
        }),
      ])),
    });
  }

  if (field.type === 'nested_object') {
    return {
      ...base,
      type: 'object',
      properties: fieldsToProperties(field.children ?? [], runtimeContext),
      ...(requiredFieldIds(field.children ?? []).length ? { required: requiredFieldIds(field.children ?? []) } : {}),
    };
  }

  return removeUndefined(base);
}

function schemaToFormilyReactions(field: SchemaField, rootFields: SchemaField[]): ISchema['x-reactions'] | undefined {
  if (!field.visibleWhen && !field.requiredWhen) return undefined;
  return (formilyField: any) => {
    const values = formilyValuesToAnswerPayload(formilyField.form?.values ?? {}, rootFields);
    const flatValues = buildFlatValueIndex(rootFields, values);
    const visible = isFieldVisible(field, flatValues);
    const required = visible && ((field.validation?.required ?? false) || isFieldConditionallyRequired(field, flatValues));

    formilyField.visible = visible;
    formilyField.display = visible ? 'visible' : 'none';
    formilyField.required = required;
  };
}

function schemaType(field: SchemaField): ISchema['type'] {
  switch (field.type) {
    case 'number':
      return 'number';
    case 'nested_object':
    case 'json_editor':
    case 'llm_interaction':
      return 'object';
    case 'tab_container':
      return 'void';
    case 'multi_select':
      return 'array';
    case 'text':
    case 'textarea':
    case 'rich_text':
    case 'single_select':
    case 'date':
      return 'string';
    case 'file_upload':
      return 'object';
    case 'show_item':
      return 'string';
    default: {
      const _exhaustive: never = field.type;
      return _exhaustive;
    }
  }
}

function validationSchema(validation?: SchemaFieldValidation): Partial<ISchema> {
  if (!validation) return {};

  return removeUndefined({
    minLength: validation.minLength,
    maxLength: validation.maxLength,
    minimum: validation.min,
    maximum: validation.max,
    pattern: validation.pattern,
  });
}

function placeholderWithValidationHint(field: SchemaField): string | undefined {
  const placeholder = field.placeholder;
  const minLength = field.validation?.minLength;
  if ((field.type !== 'text' && field.type !== 'textarea') || !placeholder || minLength == null) {
    return placeholder;
  }

  const reminder = `（至少${minLength}字符）`;
  return placeholder.includes(reminder) ? placeholder : `${placeholder}${reminder}`;
}

function fieldOptions(options?: SchemaFieldOption[]): ISchema['enum'] {
  if (!options?.length) return undefined;
  return options.map((option) => ({ label: option.label, value: option.value }));
}

function requiredFieldIds(fields: SchemaField[]): string[] {
  return fields
    .filter((field) => field.type !== 'show_item' && field.type !== 'tab_container' && field.validation?.required)
    .map((field) => field.stableId);
}

function removeUndefined<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entryValue]) => entryValue !== undefined)) as T;
}
