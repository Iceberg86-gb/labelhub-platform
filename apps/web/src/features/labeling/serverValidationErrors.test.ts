import { describe, expect, it } from 'vitest';
import { fieldErrorsToStableIdMap, selectVisibleFieldErrors } from './serverValidationErrors';

describe('serverValidationErrors', () => {
  it('maps ApiFieldError.field to stableId-keyed error lists', () => {
    const errors = fieldErrorsToStableIdMap([
      { field: 'field_0', message: '最少 5 字' },
      { field: 'field_1', message: '此字段必填' },
    ]);

    expect(errors.get('field_0')).toEqual(['最少 5 字']);
    expect(errors.get('field_1')).toEqual(['此字段必填']);
  });

  it('aggregates duplicate stableId messages in order', () => {
    const errors = fieldErrorsToStableIdMap([
      { field: 'field_0', message: '最少 5 字' },
      { field: 'field_0', message: '格式不正确' },
    ]);

    expect(errors.get('field_0')).toEqual(['最少 5 字', '格式不正确']);
  });

  it('uses client errors when no submit snapshot exists', () => {
    const clientErrors = new Map([['field_0', ['此字段必填']]]);

    expect(selectVisibleFieldErrors(clientErrors, null)).toBe(clientErrors);
  });

  it('uses server errors as a whole-map submit snapshot until the next edit', () => {
    const clientErrors = new Map([['field_0', ['前端错误']]]);
    const serverErrors = new Map([['field_0', ['后端错误']]]);

    expect(selectVisibleFieldErrors(clientErrors, serverErrors)).toBe(serverErrors);
    expect(selectVisibleFieldErrors(clientErrors, null)).toBe(clientErrors);
  });
});
