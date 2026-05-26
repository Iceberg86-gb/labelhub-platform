import type { components } from '../shared/api/generated/schema';

type CreateTaskRequest = components['schemas']['CreateTaskRequest'];

export function M6P1SubmissionLifecycleContract() {
  const request: CreateTaskRequest = {
    title: 'Lifecycle task',
    quotaTotal: 1,
    deadlineAt: '2026-05-25T00:00:00Z',
  };
  void request;
  return null;
}
