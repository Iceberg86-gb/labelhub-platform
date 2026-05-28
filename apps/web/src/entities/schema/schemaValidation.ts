import type { LinkageAtomicCondition, LinkageCondition, LinkageConditionGroup, LinkageConditionOp, SchemaDocument, SchemaField } from './schemaTypes';

export interface FieldValidationError {
  fieldPath: string;
  stableId: string;
  reason: string;
}

export function validateSchemaForUI(document: SchemaDocument): FieldValidationError[] {
  const errors: FieldValidationError[] = [];
  validateFields(document.fields ?? [], 'fields', errors);
  const fieldIndex = indexFields(document.fields ?? [], 'fields');
  validateLinkage(document.fields ?? [], 'fields', fieldIndex, errors);
  validateAcyclicLinkage(fieldIndex, errors);
  return errors;
}

export function errorsByStableId(errors: FieldValidationError[]): Map<string, FieldValidationError[]> {
  const map = new Map<string, FieldValidationError[]>();

  errors.forEach((error) => {
    const list = map.get(error.stableId) ?? [];
    list.push(error);
    map.set(error.stableId, list);
  });

  return map;
}

function validateFields(fields: SchemaField[], pathPrefix: string, errors: FieldValidationError[]) {
  fields.forEach((field, index) => {
    const fieldPath = `${pathPrefix}[${index}]`;

    if (!field.label || field.label.trim() === '') {
      errors.push({ fieldPath, stableId: field.stableId, reason: '字段标签不能为空' });
    }

    if ((field.type === 'single_select' || field.type === 'multi_select') && (!field.options || field.options.length === 0)) {
      errors.push({ fieldPath, stableId: field.stableId, reason: '选择字段至少需要一个选项' });
    }

    if (field.type === 'nested_object') {
      if (!field.children || field.children.length === 0) {
        errors.push({ fieldPath, stableId: field.stableId, reason: '嵌套对象字段需要至少一个子字段' });
        return;
      }

      field.children.forEach((child, childIndex) => {
        if (child.type === 'nested_object') {
          errors.push({
            fieldPath: `${fieldPath}.children[${childIndex}]`,
            stableId: child.stableId,
            reason: 'UI 暂不支持多层嵌套对象',
          });
        }
      });
      validateFields(field.children, `${fieldPath}.children`, errors);
    }
  });
}

const EMPTY_OPS = new Set<LinkageConditionOp>(['empty', 'notEmpty']);
const ARRAY_OPS = new Set<LinkageConditionOp>(['in', 'notIn']);
const NUMERIC_OPS = new Set<LinkageConditionOp>(['gt', 'gte', 'lt', 'lte']);

const LINKAGE_MESSAGES = {
  missingReference: '联动条件引用的字段不存在',
  selfReference: '联动条件不能引用自身',
  cycle: '联动条件存在循环依赖',
  emptyGroup: '联动条件分组至少需要一个条件',
  groupShape: '联动条件分组必须且只能设置 allOf 或 anyOf',
  atomicShape: '联动条件必须包含 field 和 op',
  emptyValue: 'empty/notEmpty 不应设置 value',
  scalarValue: '联动操作符需要标量 value',
  arrayValue: '联动操作符需要数组 value',
  numericField: '数值比较只能引用数字字段',
} as const;

interface FieldContext {
  field: SchemaField;
  path: string;
}

function indexFields(fields: SchemaField[], pathPrefix: string, index = new Map<string, FieldContext>()): Map<string, FieldContext> {
  fields.forEach((field, fieldIndex) => {
    const fieldPath = `${pathPrefix}[${fieldIndex}]`;
    if (field.stableId) {
      index.set(field.stableId, { field, path: fieldPath });
    }
    if (field.children?.length) {
      indexFields(field.children, `${fieldPath}.children`, index);
    }
  });
  return index;
}

function validateLinkage(
  fields: SchemaField[],
  pathPrefix: string,
  fieldIndex: Map<string, FieldContext>,
  errors: FieldValidationError[],
) {
  fields.forEach((field, index) => {
    const fieldPath = `${pathPrefix}[${index}]`;
    validateCondition(field, field.visibleWhen, `${fieldPath}.visibleWhen`, fieldIndex, errors);
    validateCondition(field, field.requiredWhen, `${fieldPath}.requiredWhen`, fieldIndex, errors);

    if (field.children?.length) {
      validateLinkage(field.children, `${fieldPath}.children`, fieldIndex, errors);
    }
  });
}

function validateCondition(
  owner: SchemaField,
  condition: LinkageCondition | undefined,
  conditionPath: string,
  fieldIndex: Map<string, FieldContext>,
  errors: FieldValidationError[],
) {
  if (!condition) return;

  if (isAtomicCondition(condition)) {
    validateAtomicCondition(owner, condition, conditionPath, fieldIndex, errors);
    return;
  }

  const group = condition as LinkageConditionGroup;
  const hasAllOf = Object.prototype.hasOwnProperty.call(group, 'allOf') && Boolean(group.allOf?.length);
  const hasAnyOf = Object.prototype.hasOwnProperty.call(group, 'anyOf') && Boolean(group.anyOf?.length);

  if (hasAllOf && hasAnyOf) {
    pushLinkageError(errors, owner, conditionPath, LINKAGE_MESSAGES.groupShape);
    return;
  }
  if (!hasAllOf && !hasAnyOf) {
    const suffix = Object.prototype.hasOwnProperty.call(group, 'allOf') ? 'allOf' : 'anyOf';
    pushLinkageError(errors, owner, `${conditionPath}.${suffix}`, LINKAGE_MESSAGES.emptyGroup);
    return;
  }

  const conditions = hasAllOf ? group.allOf ?? [] : group.anyOf ?? [];
  const groupPath = `${conditionPath}.${hasAllOf ? 'allOf' : 'anyOf'}`;
  conditions.forEach((atomic, index) => validateAtomicCondition(owner, atomic, `${groupPath}[${index}]`, fieldIndex, errors));
}

function validateAtomicCondition(
  owner: SchemaField,
  condition: LinkageAtomicCondition | undefined,
  conditionPath: string,
  fieldIndex: Map<string, FieldContext>,
  errors: FieldValidationError[],
) {
  if (!condition?.field || !condition.op) {
    pushLinkageError(errors, owner, conditionPath, LINKAGE_MESSAGES.atomicShape);
    return;
  }

  if (condition.field === owner.stableId) {
    pushLinkageError(errors, owner, `${conditionPath}.field`, LINKAGE_MESSAGES.selfReference);
    return;
  }

  const referenced = fieldIndex.get(condition.field);
  if (!referenced) {
    pushLinkageError(errors, owner, `${conditionPath}.field`, LINKAGE_MESSAGES.missingReference);
    return;
  }

  if (EMPTY_OPS.has(condition.op)) {
    if (condition.value !== undefined && condition.value !== null) {
      pushLinkageError(errors, owner, `${conditionPath}.value`, LINKAGE_MESSAGES.emptyValue);
    }
    return;
  }

  if (ARRAY_OPS.has(condition.op)) {
    if (!isArrayValue(condition.value)) {
      pushLinkageError(errors, owner, `${conditionPath}.value`, LINKAGE_MESSAGES.arrayValue);
    }
    return;
  }

  if (!isScalarValue(condition.value)) {
    pushLinkageError(errors, owner, `${conditionPath}.value`, LINKAGE_MESSAGES.scalarValue);
    return;
  }

  if (NUMERIC_OPS.has(condition.op) && referenced.field.type !== 'number') {
    pushLinkageError(errors, owner, `${conditionPath}.field`, LINKAGE_MESSAGES.numericField);
  }
}

function isAtomicCondition(condition: LinkageCondition): condition is LinkageAtomicCondition {
  return Object.prototype.hasOwnProperty.call(condition, 'field') || Object.prototype.hasOwnProperty.call(condition, 'op');
}

function isScalarValue(value: unknown): value is string | number | boolean {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean';
}

function isArrayValue(value: unknown): value is Array<string | number | boolean> {
  return Array.isArray(value) && value.every(isScalarValue);
}

function validateAcyclicLinkage(fieldIndex: Map<string, FieldContext>, errors: FieldValidationError[]) {
  const states = new Map<string, 'visiting' | 'visited'>();
  for (const stableId of fieldIndex.keys()) {
    if (detectCycle(stableId, fieldIndex, states, firstConditionPath(fieldIndex.get(stableId)), errors)) {
      return;
    }
  }
}

function detectCycle(
  stableId: string,
  fieldIndex: Map<string, FieldContext>,
  states: Map<string, 'visiting' | 'visited'>,
  rootConditionPath: string,
  errors: FieldValidationError[],
): boolean {
  const state = states.get(stableId);
  if (state === 'visiting') {
    const context = fieldIndex.get(stableId);
    pushLinkageError(errors, context?.field ?? { stableId, label: stableId, type: 'text' }, rootConditionPath, LINKAGE_MESSAGES.cycle);
    return true;
  }
  if (state === 'visited') {
    return false;
  }

  const context = fieldIndex.get(stableId);
  if (!context) return false;

  states.set(stableId, 'visiting');
  for (const dependency of referencedStableIds(context.field)) {
    if (detectCycle(dependency, fieldIndex, states, rootConditionPath, errors)) {
      return true;
    }
  }
  states.set(stableId, 'visited');
  return false;
}

function referencedStableIds(field: SchemaField): string[] {
  const refs = new Set<string>();
  collectReferencedStableIds(field.visibleWhen, field.stableId, refs);
  collectReferencedStableIds(field.requiredWhen, field.stableId, refs);
  return [...refs];
}

function collectReferencedStableIds(condition: LinkageCondition | undefined, ownerStableId: string, refs: Set<string>) {
  if (!condition) return;
  if (isAtomicCondition(condition)) {
    if (condition.field && condition.field !== ownerStableId) refs.add(condition.field);
    return;
  }
  condition.allOf?.forEach((atomic) => collectReferencedStableIds(atomic, ownerStableId, refs));
  condition.anyOf?.forEach((atomic) => collectReferencedStableIds(atomic, ownerStableId, refs));
}

function firstConditionPath(context?: FieldContext): string {
  if (!context) return 'fields';
  if (context.field.visibleWhen) return `${context.path}.visibleWhen`;
  return `${context.path}.requiredWhen`;
}

function pushLinkageError(errors: FieldValidationError[], field: SchemaField, fieldPath: string, reason: string) {
  errors.push({ fieldPath, stableId: field.stableId, reason });
}
