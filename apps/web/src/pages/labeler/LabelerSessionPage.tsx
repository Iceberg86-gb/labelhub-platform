import { Button, Card, Empty, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD, type AnswerPayload } from '../../entities/submission/answerPayload';
import { errorsByStableId, validatePayload } from '../../entities/labeling/payloadValidation';
import { AutosaveStatusTag } from '../../features/labeling/AutosaveStatusTag';
import { SchemaFormilyRenderer } from '../../features/labeling/formily/SchemaFormilyRenderer';
import { SubmitConfirmModal } from '../../features/labeling/SubmitConfirmModal';
import { fieldErrorsToStableIdMap, selectVisibleFieldErrors } from '../../features/labeling/serverValidationErrors';
import { useAutosave } from '../../features/labeling/useAutosave';
import { useLatestDraftQuery } from '../../features/labeling/useLatestDraftQuery';
import { useSaveDraftMutation } from '../../features/labeling/useSaveDraftMutation';
import { useSessionDetailQuery } from '../../features/labeling/useSessionDetailQuery';
import { SubmitValidationError, useSubmitMutation } from '../../features/labeling/useSubmitMutation';

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
  const detail = detailQuery.data;
  const isClaimed = detail?.session.status === 'claimed';
  const fields = detail?.schemaVersion.schemaJson.fields ?? [];

  useEffect(() => {
    if (!hasInitialized && detailQuery.isSuccess && !draftQuery.isLoading) {
      setAnswerPayload(coerceAnswerPayload(draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD));
      setHasInitialized(true);
    }
  }, [detail?.latestDraft?.payload, detailQuery.isSuccess, draftQuery.data, draftQuery.isLoading, hasInitialized]);

  const autosave = useAutosave({
    value: answerPayload ?? EMPTY_ANSWER_PAYLOAD,
    enabled: hasInitialized && isClaimed,
    onSave: async (payload) => {
      if (!sessionId) return;
      await saveDraftMutation.mutateAsync({ sessionId, payload });
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

  const handleAnswerPayloadChange = useCallback((next: AnswerPayload) => {
    setServerErrors(null);
    setAnswerPayload(next);
  }, []);

  const handleSubmitClick = () => {
    if (validationErrors.length > 0) {
      Toast.warning('请先修复字段错误再提交');
      return;
    }
    setSubmitModalOpen(true);
  };

  const handleConfirmSubmit = async () => {
    if (!sessionId) return;
    await autosave.flush();
    autosave.disable();
    try {
      const submission = await submitMutation.mutateAsync({
        sessionId,
        answerPayload: answerPayload ?? EMPTY_ANSWER_PAYLOAD,
      });
      Toast.success(`已提交 submission #${submission.id}`);
      navigate(`/labeler/submissions/${submission.id}`);
    } catch (error) {
      if (error instanceof SubmitValidationError) {
        setServerErrors(fieldErrorsToStableIdMap(error.fieldErrors));
        Toast.error(error.message);
        return;
      }
      Toast.error('提交失败,请稍后重试');
    }
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
          <AutosaveStatusTag autosave={autosave} />
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

      <Card className="labeler-session-card" bodyStyle={{ padding: 24 }}>
        <SchemaFormilyRenderer
          schemaFields={fields}
          value={answerPayload}
          onChange={handleAnswerPayloadChange}
          readOnly={!isClaimed}
          errors={visibleFieldErrors}
        />
      </Card>

      <SubmitConfirmModal
        visible={submitModalOpen}
        fields={fields}
        payload={answerPayload}
        loading={submitMutation.isPending}
        onClose={() => setSubmitModalOpen(false)}
        onConfirm={handleConfirmSubmit}
      />
    </section>
  );
}
