import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { triggerSubmitValidationFeedback } from '../../pages/labeler/LabelerSessionPage';

const fields: SchemaField[] = [
  {
    stableId: 'detailed_comment',
    label: '详细评审意见',
    type: 'text',
    validation: { required: true, minLength: 5 },
  },
];

describe('submit validation feedback', () => {
  let originalScrollIntoView: typeof HTMLElement.prototype.scrollIntoView;

  beforeEach(() => {
    originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    HTMLElement.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('triggers Formily validation and points to the first concrete field error', () => {
    document.body.innerHTML = '<div data-labeling-field-id="detailed_comment"><textarea></textarea></div>';
    const validate = vi.fn().mockRejectedValue(new Error('invalid'));
    const showWarning = vi.fn();
    const focusSpy = vi.spyOn(HTMLTextAreaElement.prototype, 'focus').mockImplementation(() => {});

    expect(triggerSubmitValidationFeedback({
      validationErrors: [{ stableId: 'detailed_comment', reason: '最少 5 字' }],
      fields,
      form: { validate },
      showWarning,
    })).toBe(true);

    expect(validate).toHaveBeenCalledTimes(1);
    expect(showWarning).toHaveBeenCalledWith('详细评审意见: 最少 5 字');
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'center', behavior: 'smooth' });
    expect(focusSpy).toHaveBeenCalledTimes(1);
  });

  it('marks the first error field for a visible mobile focus cue', () => {
    document.body.innerHTML = `
      <div data-labeling-field-id="previous_error" class="labeling-field--validation-focus"></div>
      <div data-labeling-field-id="detailed_comment"><textarea></textarea></div>
    `;
    const validate = vi.fn().mockRejectedValue(new Error('invalid'));
    const showWarning = vi.fn();

    expect(triggerSubmitValidationFeedback({
      validationErrors: [{ stableId: 'detailed_comment', reason: '最少 5 字' }],
      fields,
      form: { validate },
      showWarning,
    })).toBe(true);

    expect(document.querySelector('[data-labeling-field-id="previous_error"]')?.classList.contains('labeling-field--validation-focus')).toBe(false);
    expect(document.querySelector('[data-labeling-field-id="detailed_comment"]')?.classList.contains('labeling-field--validation-focus')).toBe(true);
  });

  it('does not trigger feedback when there are no validation errors', () => {
    const validate = vi.fn();
    const showWarning = vi.fn();

    expect(triggerSubmitValidationFeedback({
      validationErrors: [],
      fields,
      form: { validate },
      showWarning,
    })).toBe(false);

    expect(validate).not.toHaveBeenCalled();
    expect(showWarning).not.toHaveBeenCalled();
  });
});
