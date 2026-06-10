import { Button, Card, Space, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import { EmptyState, StatusBadge } from '../../shared/ui';
import { IconArrowLeft, IconBolt } from '@douyinfe/semi-icons';
import { useNavigate, useParams } from 'react-router-dom';
import { schemaFields } from '../../entities/schema/runtimeSchema';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD } from '../../entities/submission/answerPayload';
import { AiProvenanceCard } from '../../features/ai/AiProvenanceCard';
import {
  EnqueueSubmissionAiPrereviewFailure,
  useEnqueueSubmissionAiPrereviewMutation,
} from '../../features/ai/useEnqueueSubmissionAiPrereviewMutation';
import { SchemaFormilyRenderer } from '../../features/labeling/formily/SchemaFormilyRenderer';
import { useSubmissionRenderSchemaQuery } from '../../features/labeling/useSubmissionRenderSchemaQuery';

function parseId(value?: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function OwnerSubmissionPage() {
  const navigate = useNavigate();
  const { taskId: rawTaskId, submissionId: rawSubmissionId } = useParams();
  const taskId = parseId(rawTaskId);
  const submissionId = parseId(rawSubmissionId);
  const renderSchemaQuery = useSubmissionRenderSchemaQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const enqueueAiPrereview = useEnqueueSubmissionAiPrereviewMutation(taskId ?? 0);

  const renderSchema = renderSchemaQuery.data;
  const schemaVersion = renderSchema?.schemaVersion;
  const answerPayload = coerceAnswerPayload(renderSchema?.answerPayload ?? EMPTY_ANSWER_PAYLOAD);

  if (!taskId || !submissionId) {
    return <EmptyState variant="inline" title="Submission 地址无效" description="请从任务详情的提交记录入口进入。" />;
  }

  if (renderSchemaQuery.isLoading) {
    return (
      <div className="task-state-panel">
        <Spin size="large" />
      </div>
    );
  }

  if (renderSchemaQuery.isError || !schemaVersion) {
    return (
      <div className="task-state-panel">
        <EmptyState variant="inline" title="Submission 不存在或无权访问" description="请确认当前 Owner 是否拥有该任务。" />
        <Button onClick={() => navigate(`/owner/tasks/${taskId}`)}>返回任务详情</Button>
      </div>
    );
  }

  async function runAiReview() {
    if (!submissionId) return;
    try {
      const result = await enqueueAiPrereview.mutateAsync({
        submissionId,
      });
      Toast.success(result.enqueuedCount > 0 ? '已进入 AI 预审队列' : '该 submission 已在预审中或已有结果');
    } catch (error) {
      const failure = error instanceof EnqueueSubmissionAiPrereviewFailure ? error : null;
      Toast.error(failure?.userMessage ?? 'AI 预审发起失败,请稍后重试');
    }
  }

  return (
    <section className="owner-submission-page" aria-label="Owner submission detail">
      <div className="owner-submission-header">
        <div>
          <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate(`/owner/tasks/${taskId}`)}>
            返回任务详情
          </Button>
          <Typography.Title heading={3} className="page-title">
            Submission #{submissionId}
          </Typography.Title>
          <Space wrap>
            <StatusBadge tone="info">模板（Schema）版本: {schemaVersionLabel(schemaVersion)} · 提交时绑定版本</StatusBadge>
            <StatusBadge tone="accent">Task #{taskId}</StatusBadge>
          </Space>
        </div>
        <Button
          icon={<IconBolt />}
          theme="solid"
          type="primary"
          loading={enqueueAiPrereview.isPending}
          onClick={runAiReview}
        >
          发起 AI 预审
        </Button>
      </div>

      <div className="owner-submission-grid">
        <Card className="owner-submission-render-card" title="历史模板（Schema）作答" bordered={false}>
          <SchemaFormilyRenderer
            schemaFields={schemaFields(schemaVersion.schemaJson)}
            value={answerPayload}
            itemPayload={renderSchema.datasetItem?.itemPayload}
            onChange={() => {}}
            readOnly
          />
        </Card>

        <AiProvenanceCard className="owner-submission-provenance-card" submissionId={submissionId} />
      </div>
    </section>
  );
}
