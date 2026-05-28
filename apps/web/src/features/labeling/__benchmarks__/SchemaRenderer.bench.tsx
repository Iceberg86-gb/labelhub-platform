import { bench, describe } from 'vitest';
import { renderToString } from 'react-dom/server';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import { SchemaRenderer } from '../SchemaRenderer';
import { createSchemaFormilyForm, SchemaFormilyRenderer } from '../formily/SchemaFormilyRenderer';

const FIELD_COUNTS = [100, 500, 1000, 5000] as const;
const SAMPLES = 3;

const originalConsoleError = console.error.bind(console);
console.error = (...args) => {
  const firstArg = String(args[0] ?? '');
  if (firstArg.includes('useLayoutEffect does nothing on the server')) {
    return;
  }
  originalConsoleError(...args);
};

const runtime = globalThis as typeof globalThis & {
  process?: { version?: string; platform?: string; arch?: string };
};

const machine = {
  node: runtime.process?.version ?? 'unknown',
  platform: runtime.process?.platform ?? globalThis.navigator?.platform ?? 'unknown',
  arch: runtime.process?.arch ?? 'unknown',
  userAgent: globalThis.navigator?.userAgent ?? 'unknown',
};

function makeFields(count: number): SchemaField[] {
  return Array.from({ length: count }, (_, index) => ({
    stableId: `field_${index}`,
    label: `Field ${index}`,
    type: 'text',
    placeholder: `Value ${index}`,
  }));
}

function makePayload(count: number, changedIndex?: number): AnswerPayload {
  return Object.fromEntries(
    Array.from({ length: count }, (_, index) => [
      `field_${index}`,
      index === changedIndex ? `changed ${index}` : `value ${index}`,
    ]),
  );
}

function renderLegacy(count: number, changedIndex?: number) {
  renderToString(
    <SchemaRenderer
      fields={makeFields(count)}
      value={makePayload(count, changedIndex)}
      readOnly={false}
      onChange={() => {}}
    />,
  );
}

function renderFormily(count: number, changedIndex?: number) {
  renderToString(
    <SchemaFormilyRenderer
      schemaFields={makeFields(count)}
      value={makePayload(count, changedIndex)}
      readOnly={false}
      onChange={() => {}}
    />,
  );
}

function averageMs(work: () => void, samples = SAMPLES) {
  let total = 0;
  for (let sample = 0; sample < samples; sample += 1) {
    const startedAt = performance.now();
    work();
    total += performance.now() - startedAt;
  }
  return Number((total / samples).toFixed(2));
}

function countFormilySingleFieldChangeInvocations(count = 500) {
  let invocations = 0;
  const form = createSchemaFormilyForm({
    schemaFields: makeFields(count),
    value: makePayload(count),
    readOnly: false,
    onChange: () => {
      invocations += 1;
    },
  });
  form.setValuesIn(`field_${Math.floor(count / 2)}`, 'changed once');
  return invocations;
}

const legacyFirstRender = Object.fromEntries(
  FIELD_COUNTS.map((count) => [`${count}_fields_ms`, averageMs(() => renderLegacy(count))]),
);
const formilyFirstRender = Object.fromEntries(
  FIELD_COUNTS.map((count) => [`${count}_fields_ms`, averageMs(() => renderFormily(count))]),
);
const legacySingleFieldMs = averageMs(() => renderLegacy(500, 250));
const formilySingleFieldInvocations = countFormilySingleFieldChangeInvocations(500);
const formilySingleFieldMs = averageMs(() => {
  countFormilySingleFieldChangeInvocations(500);
});

export const LABELHUB_BENCHMARK_RESULTS = {
  machine,
  results: {
    legacy_first_render: legacyFirstRender,
    formily_first_render: formilyFirstRender,
    legacy_single_field_change_500: { invocations: 500, ms: legacySingleFieldMs },
    formily_single_field_change_500: {
      invocations: formilySingleFieldInvocations,
      ms: formilySingleFieldMs,
    },
  },
};

console.log(`LABELHUB_BENCHMARK_JSON ${JSON.stringify(LABELHUB_BENCHMARK_RESULTS)}`);

describe('SchemaRenderer first render', () => {
  for (const count of FIELD_COUNTS) {
    bench(`legacy ${count} fields`, () => renderLegacy(count));
    bench(`formily ${count} fields`, () => renderFormily(count));
  }
});

describe('SchemaRenderer single-field change', () => {
  bench('legacy 500 fields', () => renderLegacy(500, 250));
  bench('formily 500 fields', () => {
    countFormilySingleFieldChangeInvocations(500);
  });
});
