import { Button, Card, Empty, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
import type { Form } from '@formily/core';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD, type AnswerPayload } from '../../entities/submission/answerPayload';
import { errorsByStableId, validatePayload, type PayloadValidationError } from '../../entities/labeling/payloadValidation';
import { createVisibleSchemaFieldsSelector } from '../../entities/labeling/visibleSchemaFields';
import { AutosaveStatusTag } from '../../features/labeling/AutosaveStatusTag';
import { DatasetItemContextCard, selectDatasetItemPayload } from '../../features/labeling/DatasetItemContextCard';
import { SchemaFormilyRenderer } from '../../features/labeling/formily/SchemaFormilyRenderer';
import { SubmitConfirmModal } from '../../features/labeling/SubmitConfirmModal';
import { fieldErrorsToStableIdMap, selectVisibleFieldErrors } from '../../features/labeling/serverValidationErrors';
import { useAutosave } from '../../features/labeling/useAutosave';
import {
  applyOfflineDraftHydrationResult,
  createOfflineDraftHydrationGuard,
  useOfflineDraftBuffer,
} from '../../features/labeling/useOfflineDraftBuffer';
import { useOfflineDraftSync } from '../../features/labeling/useOfflineDraftSync';
import { useLatestDraftQuery } from '../../features/labeling/useLatestDraftQuery';
import { useSaveDraftMutation } from '../../features/labeling/useSaveDraftMutation';
import { useSessionDetailQuery } from '../../features/labeling/useSessionDetailQuery';
import { SubmitValidationError, useSubmitMutation } from '../../features/labeling/useSubmitMutation';
import { getUser } from '../../shared/api/auth-storage';
import { runLabelerSubmitWithOfflineDraft } from './labelerSubmitOfflineDraftFlow';

function parseId(value?: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function LabelerSessionPage() {
  const navigate = useNavigate();
  const params = useParams();
  const sessionId = parseId(params.sessionId);
  const detailQuery = useSessionDetailQuery(sessionId ?? 0, { enabled: Boolean(sessionId) });
  const draftQuery = useLatestDraftQuery(sessionId ?? 0, { enabled: detailQuery.isSuccess });
  const saveDraftMutation = useSaveDraftMutation();
  const submitMutation = useSubmitMutation();

  const [answerPayload, setAnswerPayload] = useState<AnswerPayload | null>(null);
  const [serverErrors, setServerErrors] = useState<Map<string, string[]> | null>(null);
  const [hasInitialized, setHasInitialized] = useState(false);
  const [submitModalOpen, setSubmitModalOpen] = useState(false);
  const formRef = useRef<Form<Record<string, unknown>> | null>(null);
  const visibleFieldsSelector = useMemo(() => createVisibleSchemaFieldsSelector(), []);
  const userId = getUser()?.id ?? null;
  const {
    hydrate: hydrateOfflineDraftBuffer,
    bufferPending: bufferPendingOfflineDraft,
    status: offlineDraftStatus,
  } = useOfflineDraftBuffer();
  const {
    status: offlineDraftSyncStatus,
    retryPending: retryOfflineDraftSync,
    syncPendingForSubmit,
    discardPending: discardOfflineDraft,
  } = useOfflineDraftSync({
    enabled: Boolean(userId && sessionId),
    sessionId,
  });
  const detail = detailQuery.data;
  const isClaimed = detail?.session.status === 'claimed';
  const fields = detail?.schemaVersion.schemaJson.fields ?? [];
  const datasetItemContext = useMemo(
    () =>
      detail
        ? selectDatasetItemPayload({
            claimSnapshot: detail.session.claimSnapshot,
            datasetItemPayload: detail.datasetItem.itemPayload,
          })
        : { payload: null, source: 'none' as const },
    [detail],
  );

  useEffect(() => {
    const hydrationGuard = createOfflineDraftHydrationGuard();

    if (!hasInitialized && sessionId && detail && detailQuery.isSuccess && !draftQuery.isLoading) {
      const serverPayload = coerceAnswerPayload(draftQuery.data?.payload ?? detail.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD);
      void hydrateOfflineDraftBuffer({
        userId,
        sessionId,
        schemaVersionId: detail.schemaVersion.id,
        serverPayload,
      }).then((result) => {
        applyOfflineDraftHydrationResult(result, hydrationGuard, (payload) => {
          setAnswerPayload(coerceAnswerPayload(payload));
          setHasInitialized(true);
        });
      });
    }

    return () => {
      hydrationGuard.cancel();
    };
  }, [
    detail,
    detailQuery.isSuccess,
    draftQuery.data,
    draftQuery.isLoading,
    hasInitialized,
    hydrateOfflineDraftBuffer,
    sessionId,
    userId,
  ]);

  const autosave = useAutosave({
    value: answerPayload ?? EMPTY_ANSWER_PAYLOAD,
    enabled: hasInitialized && isClaimed,
    onSave: async (payload) => {
      if (!sessionId || !detail) return;
      try {
        await saveDraftMutation.mutateAsync({ sessionId, payload });
      } catch (error) {
        await bufferPendingOfflineDraft({
          userId,
          sessionId,
          schemaVersionId: detail.schemaVersion.id,
          payload,
        });
        throw error;
      }
    },
  });

  const validationErrors = useMemo(
    () => (detail && answerPayload ? validatePayload(fields, answerPayload) : []),
    [answerPayload, detail, fields],
  );
  const fieldErrors = useMemo(() => errorsByStableId(validationErrors), [validationErrors]);
  const visibleFieldErrors = useMemo(
    () => selectVisibleFieldErrors(fieldErrors, serverErrors),
    [fieldErrors, serverErrors],
  );
  const visibleFields = useMemo(
    () => visibleFieldsSelector(fields, answerPayload ?? EMPTY_ANSWER_PAYLOAD),
    [answerPayload, fields, visibleFieldsSelector],
  );

  const handleAnswerPayloadChange = useCallback((next: AnswerPayload) => {
    setServerErrors(null);
    setAnswerPayload(next);
  }, []);

  const handleFormReady = useCallback((form: Form<Record<string, unknown>>) => {
    formRef.current = form;
  }, []);

  const handleSubmitClick = () => {
    if (triggerSubmitValidationFeedback({ validationErrors, fields, form: formRef.current })) {
      return;
    }
    setSubmitModalOpen(true);
  };

  const handleConfirmSubmit = async () => {
    if (!sessionId) return;
    const finalPayload = answerPayload ?? EMPTY_ANSWER_PAYLOAD;
    await runLabelerSubmitWithOfflineDraft({
      sessionId,
      userId,
      schemaVersionId: detail?.schemaVersion.id ?? null,
      finalPayload,
      flush: autosave.flush,
      disable: autosave.disable,
      bufferPending: bufferPendingOfflineDraft,
      preSync: syncPendingForSubmit,
      submit: (payload) => submitMutation.mutateAsync({ sessionId, answerPayload: payload }),
      clearPending: discardOfflineDraft,
      onSuccess: (submission) => {
        Toast.success(`已提交 submission #${submission.id}`);
        navigate(`/labeler/submissions/${submission.id}`);
      },
      onBlocked: (message) => {
        Toast.error(message);
      },
      onValidationError: (error: SubmitValidationError) => {
        setServerErrors(fieldErrorsToStableIdMap(error.fieldErrors));
        Toast.error(error.message);
      },
      onGenericError: () => {
        Toast.error('提交失败,请稍后重试');
      },
    });
  };

  if (!sessionId) {
    return <Empty title="Session 地址无效" description="请从任务广场或我的数据进入作答页。" />;
  }

  if (detailQuery.isLoading || (detailQuery.isSuccess && !hasInitialized)) {
    return (
      <div className="task-state-panel">
        <Spin size="large" />
      </div>
    );
  }

  if (detailQuery.isError || !detail || !answerPayload) {
    return <Empty title="session 不存在或无权访问" description="请确认当前账号是否拥有这个作答会话。" />;
  }

  return (
    <section className="labeler-session-page" aria-label="Labeler session workspace">
      <div className="labeler-session-header">
        <div>
          <Typography.Title heading={3} className="page-title">
            {detail.task.title}
          </Typography.Title>
          <Space>
            <Tag color="blue">Schema {schemaVersionLabel(detail.schemaVersion)}</Tag>
            <Tag color={isClaimed ? 'green' : 'grey'}>Session #{detail.session.id}</Tag>
            <Typography.Text type="tertiary">{detail.task.description || '暂无描述'}</Typography.Text>
          </Space>
        </div>
        <div className="labeler-session-actions">
          <AutosaveStatusTag
            autosave={autosave}
            offlineDraft={offlineDraftStatus}
            offlineSync={offlineDraftSyncStatus}
            onRetryOfflineDraftSync={sessionId ? () => void retryOfflineDraftSync(sessionId) : undefined}
          />
          <Button
            icon={<IconSend />}
            type="primary"
            disabled={!isClaimed}
            loading={submitMutation.isPending}
            onClick={handleSubmitClick}
          >
            提交
          </Button>
        </div>
      </div>

      <DatasetItemContextCard itemPayload={datasetItemContext.payload} sourceLabel={datasetItemContext.source} />

      <Card className="labeler-session-card" bodyStyle={{ padding: 24 }}>
        <SchemaFormilyRenderer
          schemaFields={visibleFields}
          value={answerPayload}
          onChange={handleAnswerPayloadChange}
          readOnly={!isClaimed}
          errors={visibleFieldErrors}
          onFormReady={handleFormReady}
        />
      </Card>

      <SubmitConfirmModal
        visible={submitModalOpen}
        fields={visibleFields}
        payload={answerPayload}
        loading={submitMutation.isPending}
        onClose={() => setSubmitModalOpen(false)}
        onConfirm={handleConfirmSubmit}
      />
    </section>
  );
}

interface SubmitValidationFeedbackOptions {
  validationErrors: PayloadValidationError[];
  fields: SchemaField[];
  form: Pick<Form<Record<string, unknown>>, 'validate'> | null;
  showWarning?: (message: string) => void;
  root?: ParentNode;
}

export function triggerSubmitValidationFeedback({
  validationErrors,
  fields,
  form,
  showWarning = Toast.warning,
  root = document,
}: SubmitValidationFeedbackOptions): boolean {
  const firstError = validationErrors[0];
  if (!firstError) {
    return false;
  }

  void form?.validate().catch(() => undefined);
  showWarning(`${fieldLabelForError(fields, firstError.stableId)}: ${firstError.reason}`);
  scrollToFieldError(firstError.stableId, root);
  return true;
}

function fieldLabelForError(fields: SchemaField[], stableId: string): string {
  return findFieldLabel(fields, stableId) ?? (stableId ? stableId : '字段');
}

function findFieldLabel(fields: SchemaField[], stableId: string): string | null {
  for (const field of fields) {
    if (field.stableId === stableId) {
      return field.label || field.stableId;
    }
    if (field.type === 'nested_object') {
      const childLabel = findFieldLabel(field.children ?? [], stableId);
      if (childLabel) {
        return childLabel;
      }
    }
  }
  return null;
}

function scrollToFieldError(stableId: string, root: ParentNode) {
  const target = root.querySelector<HTMLElement>(`[data-labeling-field-id="${escapeAttributeValue(stableId)}"]`);
  if (!target) {
    return;
  }

  target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  target.querySelector<HTMLElement>('input, textarea, button, [role="combobox"], [tabindex]:not([tabindex="-1"])')?.focus();
}

function escapeAttributeValue(value: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(value);
  }
  return value.replace(/["\\]/g, '\\$&');
}
