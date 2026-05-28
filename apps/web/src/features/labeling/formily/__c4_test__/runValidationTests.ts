import { createForm } from '@formily/core';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { validatePayload } from '../../../../entities/labeling/payloadValidation';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from '../adapters/schemaToFormilyISchema';
import { schemaToFormilyValidators } from '../adapters/schemaToFormilyValidators';

let assertions = 0;

function field(overrides: Partial<SchemaField> & Pick<SchemaField, 'stableId' | 'type'>): SchemaField {
  return {
    stableId: overrides.stableId,
    type: overrides.type,
    label: overrides.label ?? overrides.stableId,
    placeholder: overrides.placeholder,
    help: overrides.help,
    validation: overrides.validation,
    options: overrides.options,
    children: overrides.children,
  };
}

function equal(actual: unknown, expected: unknown, message?: string) {
  if (actual !== expected) {
    throw new Error(message ?? `Expected ${String(actual)} to equal ${String(expected)}`);
  }
}

function ok(value: unknown, message?: string) {
  if (!value) {
    throw new Error(message ?? 'Expected value to be truthy');
  }
}

function includes(list: readonly string[], expected: string, message?: string) {
  if (!list.some((item) => item.includes(expected))) {
    throw new Error(message ?? `Expected ${JSON.stringify(list)} to include ${expected}`);
  }
}

async function check(name: string, assertion: () => void | Promise<void>) {
  await assertion();
  assertions += 1;
  console.log(`ok ${assertions} - ${name}`);
}

async function validateField(fieldSpec: SchemaField, value: unknown): Promise<string[]> {
  const form = createForm();
  const formilyField = form.createField({
    name: fieldSpec.stableId,
    validator: schemaToFormilyValidators(fieldSpec),
  });
  formilyField.setValue(value);
  try {
    await formilyField.validate();
  } catch {
    // Formily stores the user-facing messages on the field; tests assert those.
  }
  return formilyField.selfErrors;
}

await check('required fields project to Formily validation errors', async () => {
  const errors = await validateField(field({ stableId: 'required_text', type: 'text', validation: { required: true } }), '');
  includes(errors, '此字段必填');
});

await check('minLength projects to Formily validation errors', async () => {
  const errors = await validateField(field({ stableId: 'short_text', type: 'text', validation: { minLength: 2 } }), 'a');
  includes(errors, '最少 2 字');
});

await check('maxLength projects to Formily validation errors', async () => {
  const errors = await validateField(field({ stableId: 'long_text', type: 'text', validation: { maxLength: 12 } }), 'abcdefghijklm');
  includes(errors, '最多 12 字');
});

await check('pattern projects to Formily validation errors', async () => {
  const errors = await validateField(field({ stableId: 'pattern_text', type: 'text', validation: { pattern: '^[a-z ]+$' } }), 'ABC123');
  includes(errors, '格式不正确');
});

await check('values passing Formily validation also pass payloadValidation for projected rules', async () => {
  const fields = [field({ stableId: 'name', type: 'text', validation: { required: true, minLength: 2, maxLength: 12, pattern: '^[a-z ]+$' } })];
  const valid = { name: 'hello world' } satisfies AnswerPayload;
  const invalid = { name: '' } satisfies AnswerPayload;

  equal((await validateField(fields[0], valid.name)).length, 0);
  equal(validatePayload(fields, valid).length, 0);

  ok((await validateField(fields[0], invalid.name)).length > 0);
  ok(validatePayload(fields, invalid).length > 0);

  const schema = schemaToFormilyISchema(fields);
  ok((schema.properties as Record<string, any>).name?.['x-validator']);
});

await check('number min and max project to Formily validation errors', async () => {
  const fieldSpec = field({ stableId: 'score', type: 'number', validation: { min: 0, max: 100 } });
  includes(await validateField(fieldSpec, -5), '不能小于 0');
  includes(await validateField(fieldSpec, 200), '不能大于 100');

  const form = createForm({ initialValues: { score: 42 } });
  const payload = formilyValuesToAnswerPayload(form.values, [fieldSpec]);
  equal(validatePayload([fieldSpec], payload).length, 0);
});

console.log(`C4 validation assertions passed: ${assertions}`);
