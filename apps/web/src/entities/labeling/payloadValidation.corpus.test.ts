import corpus from '../../../../../packages/contracts/fixtures/validation-corpus.json';
import { describe, expect, it } from 'vitest';
import type { SchemaDocument } from '../schema/schemaTypes';
import { schemaFields } from '../schema/runtimeSchema';
import type { AnswerPayload } from '../submission/answerPayload';
import { validatePayload } from './payloadValidation';

interface ExpectedError {
  stableId: string;
  reason: string;
}

interface CorpusCase {
  caseId: string;
  description: string;
  schema: SchemaDocument;
  payload: AnswerPayload;
  expectedErrors: ExpectedError[];
  expectSymmetry: boolean;
}

const corpusCases = corpus as unknown as CorpusCase[];
const symmetricCases = corpusCases.filter((testCase) => testCase.expectSymmetry);
const asymmetricCases = corpusCases.filter((testCase) => !testCase.expectSymmetry);

describe('payloadValidation shared corpus', () => {
  it('declares exactly one known asymmetric case', () => {
    expect(asymmetricCases.map((testCase) => testCase.caseId)).toEqual(['number-min-scientific-known-asymmetry']);
  });

  it.each(symmetricCases)('$caseId', (testCase) => {
    expect(validatePayload(schemaFields(testCase.schema), testCase.payload)).toEqual(testCase.expectedErrors);
  });

  it('documents the scientific notation message asymmetry without hiding the rule failure', () => {
    const testCase = asymmetricCases[0];
    const actual = validatePayload(schemaFields(testCase.schema), testCase.payload);

    expect(actual).toHaveLength(1);
    expect(actual[0]?.stableId).toBe('score');
    expect(actual[0]?.reason).toContain('不能小于');
    expect(actual).not.toEqual(testCase.expectedErrors);
  });
});
