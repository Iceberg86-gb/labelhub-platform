import { describe, expect, it } from 'vitest';
import { buildReviewerQueueNavigation, reviewerCompletionPath } from './reviewerQueueNavigation';

describe('buildReviewerQueueNavigation', () => {
  it('returns position and neighboring submissions for the current pending queue', () => {
    const navigation = buildReviewerQueueNavigation({
      currentSubmissionId: 502,
      submissions: [{ id: 501 }, { id: 502 }, { id: 503 }],
    });

    expect(navigation).toEqual({
      position: 2,
      total: 3,
      previousSubmissionId: 501,
      nextSubmissionId: 503,
    });
  });

  it('falls back to an empty navigation when the current submission is not in the queue', () => {
    const navigation = buildReviewerQueueNavigation({
      currentSubmissionId: 999,
      submissions: [{ id: 501 }, { id: 502 }],
    });

    expect(navigation).toEqual({
      position: 0,
      total: 2,
      previousSubmissionId: null,
      nextSubmissionId: null,
    });
  });

  it('routes to the next submission after verdict and falls back to the queue when the batch is complete', () => {
    expect(reviewerCompletionPath({ nextSubmissionId: 503, reviewLevel: 'reviewer' }))
      .toBe('/reviewer/submissions/503?reviewLevel=reviewer');

    expect(reviewerCompletionPath({ nextSubmissionId: null, reviewLevel: 'senior_reviewer' }))
      .toBe('/reviewer/submissions?reviewLevel=senior_reviewer');
  });
});
