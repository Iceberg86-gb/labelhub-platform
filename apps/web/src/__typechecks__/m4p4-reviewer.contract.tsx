import { ReviewerQueuePage } from '../pages/reviewer/ReviewerQueuePage';
import { ReviewerSubmissionPage } from '../pages/reviewer/ReviewerSubmissionPage';
import { useCreateLedgerEntryMutation } from '../features/quality/useCreateLedgerEntryMutation';
import { useLedgerEntriesQuery } from '../features/quality/useLedgerEntriesQuery';
import { useReviewerQueueQuery } from '../features/quality/useReviewerQueueQuery';
import { useSubmissionVerdictQuery } from '../features/quality/useSubmissionVerdictQuery';
import type { ReviewerSubmissionSummary, Verdict } from '../entities/quality/qualityTypes';

const verdict: Verdict | null = null;
const reviewerSubmission: ReviewerSubmissionSummary | null = null;

export function M4P4ReviewerContract() {
  useReviewerQueueQuery({ page: 1, size: 20, verdict: 'pending' });
  useSubmissionVerdictQuery(1, { enabled: true });
  useLedgerEntriesQuery(1, { page: 1, size: 50, enabled: true });
  useCreateLedgerEntryMutation();

  return (
    <>
      <ReviewerQueuePage />
      <ReviewerSubmissionPage />
      <span>{verdict?.status ?? reviewerSubmission?.verdict.status}</span>
    </>
  );
}
