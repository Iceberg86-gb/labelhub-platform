import corpus from '../../../../../packages/contracts/fixtures/linkage-corpus.json';
import { describe, expect, it } from 'vitest';
import type { SchemaDocument } from '../schema/schemaTypes';
import { validateSchemaForUI } from '../schema/schemaValidation';
import type { AnswerPayload } from '../submission/answerPayload';
import { validatePayload } from './payloadValidation';

interface ExpectedError {
  stableId: string;
  reason: string;
}

interface ExpectedPublishError {
  fieldPath: string;
  reason: string;
}

interface CorpusCase {
  kind: 'runtime' | 'publish';
  caseId: string;
  description: string;
  schema: SchemaDocument;
  payload?: AnswerPayload;
  expectedErrors?: ExpectedError[];
  expectedPublishErrors?: ExpectedPublishError[];
  expectSymmetry: boolean;
}

const corpusCases = corpus as unknown as CorpusCase[];
const runtimeCases = corpusCases.filter((testCase) => testCase.kind === 'runtime');
const publishCases = corpusCases.filter((testCase) => testCase.kind === 'publish');

describe('linkage shared corpus', () => {
  it('declares no known asymmetric cases', () => {
    expect(corpusCases.filter((testCase) => !testCase.expectSymmetry).map((testCase) => testCase.caseId)).toEqual([]);
  });

  it.each(runtimeCases)('$caseId', (testCase) => {
    expect(validatePayload(testCase.schema.fields, testCase.payload ?? {})).toEqual(testCase.expectedErrors ?? []);
  });

  it.each(publishCases)('$caseId', (testCase) => {
    const actual = validateSchemaForUI(testCase.schema).map(({ fieldPath, reason }) => ({ fieldPath, reason }));
    expect(actual).toEqual(testCase.expectedPublishErrors ?? []);
  });
});
