import type { components } from '../shared/api/generated/schema';

type AiFieldFindingPayload = components['schemas']['AiFieldFindingPayload'];
type ReviewerOverallVerdictPayload = components['schemas']['ReviewerOverallVerdictPayload'];
type QualityLedgerEntry = components['schemas']['QualityLedgerEntry'];

export function M5P5AiLedgerContract() {
  const aiPayload: AiFieldFindingPayload = {
    fieldPath: 'field-title',
    severity: 'warning',
    finding: 'Title may need review',
    confidence: 0.8,
  };
  const reviewerPayload: ReviewerOverallVerdictPayload = {
    verdict: 'approve',
    reviewLevel: 'reviewer',
  };
  const aiEntry: QualityLedgerEntry = {
    id: 1,
    submissionId: 2,
    taskId: 3,
    entryType: 'ai_field_finding',
    actorType: 'ai',
    actorUserId: null,
    aiCallId: 4,
    payload: aiPayload,
    createdAt: '2026-05-25T00:00:00Z',
  };
  const reviewerEntry: QualityLedgerEntry = {
    ...aiEntry,
    entryType: 'reviewer_overall_verdict',
    actorType: 'reviewer',
    actorUserId: 1003,
    aiCallId: null,
    payload: reviewerPayload,
  };
  void aiEntry;
  void reviewerEntry;
  return null;
}
