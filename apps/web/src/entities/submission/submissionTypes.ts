import type { components } from '../../shared/api/generated/schema';

export type Session = components['schemas']['Session'];
export type SessionDetail = components['schemas']['SessionDetail'];
export type SessionStatus = components['schemas']['SessionStatus'];
export type LabelerSessionWorkStatus = components['schemas']['LabelerSessionWorkStatus'];
export type LabelerSessionSummary = components['schemas']['LabelerSessionSummary'];
export type SessionFinalVerdict = components['schemas']['SessionFinalVerdict'];
export type Submission = components['schemas']['Submission'];
export type SubmissionRenderSchema = components['schemas']['SubmissionRenderSchema'];
export type Draft = components['schemas']['Draft'];
export type SubmitSessionRequest = components['schemas']['SubmitSessionRequest'];
export type SaveDraftRequest = components['schemas']['SaveDraftRequest'];
export type MarketplaceTask = components['schemas']['MarketplaceTask'];
export type PagedMarketplaceTasks = components['schemas']['PagedMarketplaceTasks'];
export type PagedSessions = components['schemas']['PagedSessions'];
export type ClaimTaskItemsResult = components['schemas']['ClaimTaskItemsResult'];

export const SESSION_STATUSES = ['claimed', 'submitted', 'returned_for_revision', 'abandoned'] satisfies SessionStatus[];

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  claimed: '进行中',
  submitted: '已提交',
  returned_for_revision: '待修改',
  abandoned: '已放弃',
};

export const LABELER_WORK_STATUSES = [
  'in_progress',
  'submitted',
  'approved',
  'rejected',
  'returned_for_revision',
  'abandoned',
] satisfies LabelerSessionWorkStatus[];

export const LABELER_WORK_STATUS_LABELS: Record<LabelerSessionWorkStatus, string> = {
  in_progress: '进行中',
  submitted: '审核中',
  approved: '通过',
  rejected: '未通过',
  returned_for_revision: '待修改',
  abandoned: '已放弃',
};
