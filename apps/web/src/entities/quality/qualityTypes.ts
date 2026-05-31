import type { components } from '../../shared/api/generated/schema';

export type QualityLedgerEntry = components['schemas']['QualityLedgerEntry'];
export type QualityLedgerEntryType = components['schemas']['QualityLedgerEntryType'];
export type QualityLedgerEntryPayload = components['schemas']['QualityLedgerEntryPayload'];
export type ReviewerOverallVerdictPayload = components['schemas']['ReviewerOverallVerdictPayload'];
export type CreateLedgerEntryRequest = components['schemas']['CreateLedgerEntryRequest'];
export type PagedQualityLedgerEntries = components['schemas']['PagedQualityLedgerEntries'];
export type Verdict = components['schemas']['Verdict'];
export type ReviewerSubmissionSummary = components['schemas']['ReviewerSubmissionSummary'];
export type PagedReviewerSubmissions = components['schemas']['PagedReviewerSubmissions'];
export type ReviewLevel = components['schemas']['ReviewLevel'];

export type VerdictStatus = Verdict['status'];
export type ReviewerVerdict = ReviewerOverallVerdictPayload['verdict'];

export const VERDICT_STATUS_LABELS: Record<VerdictStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
};

export const VERDICT_STATUS_COLORS = {
  pending: 'orange',
  approved: 'green',
  rejected: 'red',
} as const satisfies Record<VerdictStatus, 'orange' | 'green' | 'red'>;

export const REVIEWER_VERDICT_LABELS: Record<ReviewerVerdict, string> = {
  approve: '通过',
  reject: '拒绝',
};

export const REVIEW_LEVEL_LABELS: Record<ReviewLevel, string> = {
  reviewer: '初审',
  senior_reviewer: '复核',
};
