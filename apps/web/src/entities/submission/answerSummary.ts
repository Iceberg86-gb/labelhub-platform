import type { SchemaField } from '../schema/schemaTypes';
import type { AnswerPayload } from './answerPayload';
import { isEmptyAnswerValue, isAnswerPayload } from './answerPayload';

export interface AnswerSummary {
  totalCount: number;
  answeredCount: number;
  unansweredCount: number;
}

export function summarizeAnswerPayload(fields: SchemaField[], payload: AnswerPayload): AnswerSummary {
  let totalCount = 0;
  let answeredCount = 0;

  const visit = (items: SchemaField[], current: AnswerPayload) => {
    items.forEach((field) => {
      totalCount += 1;
      const value = current[field.stableId];
      if (!isEmptyAnswerValue(value)) {
        answeredCount += 1;
      }
      if (field.type === 'nested_object') {
        visit(field.children ?? [], isAnswerPayload(value) ? value : {});
        return;
      }
      if (field.type === 'tab_container') {
        totalCount -= 1;
        field.tabs?.forEach((tab) => visit(tab.children ?? [], current));
      }
    });
  };

  visit(fields, payload);
  return { totalCount, answeredCount, unansweredCount: totalCount - answeredCount };
}
