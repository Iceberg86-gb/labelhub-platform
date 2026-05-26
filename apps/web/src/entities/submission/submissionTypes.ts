import type { components } from '../../shared/api/generated/schema';

export type Session = components['schemas']['Session'];
export type SessionDetail = components['schemas']['SessionDetail'];
export type SessionStatus = components['schemas']['SessionStatus'];
export type Submission = components['schemas']['Submission'];
export type SubmissionRenderSchema = components['schemas']['SubmissionRenderSchema'];
export type Draft = components['schemas']['Draft'];
export type SubmitSessionRequest = components['schemas']['SubmitSessionRequest'];
export type SaveDraftRequest = components['schemas']['SaveDraftRequest'];
export type MarketplaceTask = components['schemas']['MarketplaceTask'];
export type PagedMarketplaceTasks = components['schemas']['PagedMarketplaceTasks'];
export type PagedSessions = components['schemas']['PagedSessions'];

export const SESSION_STATUSES = ['claimed', 'submitted', 'abandoned'] satisfies SessionStatus[];

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  claimed: '进行中',
  submitted: '已提交',
  abandoned: '已放弃',
};
