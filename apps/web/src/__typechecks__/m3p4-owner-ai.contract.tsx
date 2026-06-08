import type { AiReviewResult } from '../entities/ai/aiTypes';
import { AiProvenanceCard } from '../features/ai/AiProvenanceCard';
import { AiReviewDrawer } from '../features/ai/AiReviewDrawer';
import { useEnqueueSubmissionAiPrereviewMutation } from '../features/ai/useEnqueueSubmissionAiPrereviewMutation';
import { useSubmissionAiProvenanceQuery } from '../features/ai/useSubmissionAiProvenanceQuery';
import { OwnerTaskSubmissionsSection } from '../features/submission/OwnerTaskSubmissionsSection';
import { useOwnerTaskSubmissionsQuery } from '../features/submission/useOwnerTaskSubmissionsQuery';
import { OwnerSubmissionPage } from '../pages/owner/OwnerSubmissionPage';

const example: AiReviewResult | null = null;

export function M3P4OwnerAiContract() {
  useSubmissionAiProvenanceQuery(1, { enabled: true });
  useEnqueueSubmissionAiPrereviewMutation(1);
  useOwnerTaskSubmissionsQuery(1, { page: 1, size: 20, enabled: true });

  return (
    <>
      <OwnerSubmissionPage />
      <OwnerTaskSubmissionsSection taskId={1} />
      <AiProvenanceCard submissionId={1} />
      <AiReviewDrawer open={false} loading={false} result={example} onClose={() => {}} />
    </>
  );
}
