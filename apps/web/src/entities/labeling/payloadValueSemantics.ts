import { isEmptyAnswerValue } from '../submission/answerPayload';

export function isPayloadValueEmpty(value: unknown): boolean {
  return isEmptyAnswerValue(value);
}
