import type { AnswerPayload } from '../../entities/submission/answerPayload';
import type { BufferPendingOfflineDraftResult } from '../../features/labeling/useOfflineDraftBuffer';
import { SubmitValidationError } from '../../features/labeling/useSubmitMutation';
import type { OfflineDraftSubmitPreSyncResult } from '../../features/labeling/useOfflineDraftSync';

export type LabelerSubmitOfflineDraftDeps<Submission> = {
  sessionId: number;
  userId: number | null;
  schemaVersionId: number | null;
  finalPayload: AnswerPayload;
  flush: () => Promise<void>;
  disable: () => void;
  bufferPending: (input: {
    userId: number | null;
    sessionId: number;
    schemaVersionId: number;
    payload: AnswerPayload;
  }) => Promise<BufferPendingOfflineDraftResult>;
  preSync: (sessionId: number) => Promise<OfflineDraftSubmitPreSyncResult>;
  submit: (payload: AnswerPayload) => Promise<Submission>;
  clearPending: (sessionId: number) => Promise<void>;
  onSuccess: (submission: Submission) => void;
  onBlocked: (message: string) => void;
  onValidationError: (error: SubmitValidationError) => void;
  onGenericError: (error: unknown) => void;
};

export type LabelerSubmitOfflineDraftResult =
  | { kind: 'submitted' }
  | { kind: 'blocked' }
  | { kind: 'validation-error' }
  | { kind: 'submit-error' };

export async function runLabelerSubmitWithOfflineDraft<Submission>({
  sessionId,
  userId,
  schemaVersionId,
  finalPayload,
  flush,
  disable,
  bufferPending,
  preSync,
  submit,
  clearPending,
  onSuccess,
  onBlocked,
  onValidationError,
  onGenericError,
}: LabelerSubmitOfflineDraftDeps<Submission>): Promise<LabelerSubmitOfflineDraftResult> {
  await flush();
  disable();

  if (userId && schemaVersionId) {
    await bufferPending({ userId, sessionId, schemaVersionId, payload: finalPayload });
    const preSyncResult = await preSync(sessionId);
    if (preSyncResult.kind === 'block-submit') {
      onBlocked(preSyncResult.message);
      return { kind: 'blocked' };
    }
  }

  try {
    const submission = await submit(finalPayload);
    if (userId) {
      try {
        await clearPending(sessionId);
      } catch {
        // Local cleanup is best-effort after the canonical submit succeeds.
      }
    }
    onSuccess(submission);
    return { kind: 'submitted' };
  } catch (error) {
    if (error instanceof SubmitValidationError) {
      onValidationError(error);
      return { kind: 'validation-error' };
    }
    onGenericError(error);
    return { kind: 'submit-error' };
  }
}
