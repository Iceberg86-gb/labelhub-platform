type ReviewerQueueItem = {
  id: number;
};

export type ReviewerQueueNavigation = {
  position: number;
  total: number;
  previousSubmissionId: number | null;
  nextSubmissionId: number | null;
};

export type ReviewerReviewLevel = 'reviewer' | 'senior_reviewer';

export function buildReviewerQueueNavigation({
  currentSubmissionId,
  submissions,
}: {
  currentSubmissionId: number | null;
  submissions: ReviewerQueueItem[];
}): ReviewerQueueNavigation {
  const total = submissions.length;
  const index = submissions.findIndex((submission) => submission.id === currentSubmissionId);
  if (index < 0) {
    return {
      position: 0,
      total,
      previousSubmissionId: null,
      nextSubmissionId: null,
    };
  }

  return {
    position: index + 1,
    total,
    previousSubmissionId: submissions[index - 1]?.id ?? null,
    nextSubmissionId: submissions[index + 1]?.id ?? null,
  };
}

export function reviewerQueuePath(reviewLevel: ReviewerReviewLevel) {
  return `/reviewer/submissions?reviewLevel=${reviewLevel}`;
}

export function reviewerSubmissionPath(submissionId: number, reviewLevel: ReviewerReviewLevel) {
  return `/reviewer/submissions/${submissionId}?reviewLevel=${reviewLevel}`;
}

export function reviewerCompletionPath({
  nextSubmissionId,
  reviewLevel,
}: {
  nextSubmissionId: number | null;
  reviewLevel: ReviewerReviewLevel;
}) {
  return nextSubmissionId
    ? reviewerSubmissionPath(nextSubmissionId, reviewLevel)
    : reviewerQueuePath(reviewLevel);
}
