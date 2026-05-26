import { Button, Card, Empty, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconArrowLeft, IconBolt } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD } from '../../entities/submission/answerPayload';
import { AiProvenanceCard } from '../../features/ai/AiProvenanceCard';
import { AiReviewDrawer } from '../../features/ai/AiReviewDrawer';
import { TriggerAiReviewFailure, useTriggerAiReviewMutation } from '../../features/ai/useTriggerAiReviewMutation';
import { SchemaRenderer } from '../../features/labeling/SchemaRenderer';
import { useSubmissionRenderSchemaQuery } from '../../features/labeling/useSubmissionRenderSchemaQuery';
import type { AiReviewResult } from '../../entities/ai/aiTypes';

const DEFAULT_PROMPT_VERSION = 'm3-owner-review-v1';
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
  const triggerAiReview = useTriggerAiReviewMutation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [latestResult, setLatestResult] = useState<AiReviewResult | null>(null);

  const renderSchema = renderSchemaQuery.data;
  const schemaVersion = renderSchema?.schemaVersion;
  const answerPayload = coerceAnswerPayload(renderSchema?.answerPayload ?? EMPTY_ANSWER_PAYLOAD);

  if (!taskId || !submissionId) {
    return <Empty title="Submission 地址无效" description="请从任务详情的提交记录入口进入。" />;
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
        <Empty title="Submission 不存在或无权访问" description="请确认当前 Owner 是否拥有该任务。" />
        <Button onClick={() => navigate(`/owner/tasks/${taskId}`)}>返回任务详情</Button>
      </div>
    );
  }

  async function runAiReview() {
    if (!submissionId) return;
    setDrawerOpen(true);
    try {
      const result = await triggerAiReview.mutateAsync({ submissionId, promptVersion: DEFAULT_PROMPT_VERSION });
      setLatestResult(result);
      Toast.success(result.idempotencyHit ? '已复用历史 AI 检查结果' : 'AI 检查完成');
    } catch (error) {
      const failure = error instanceof TriggerAiReviewFailure ? error : null;
      Toast.error(failure?.userMessage ?? 'AI 检查失败,请稍后重试');
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
            <Tag color="purple">Schema 版本: {schemaVersionLabel(schemaVersion)} · 提交时绑定版本</Tag>
            <Tag color="blue">Task #{taskId}</Tag>
          </Space>
        </div>
        <Button icon={<IconBolt />} theme="solid" type="primary" loading={triggerAiReview.isPending} onClick={runAiReview}>
          AI 检查
        </Button>
      </div>

      <div className="owner-submission-grid">
        <Card className="owner-submission-render-card" title="历史 Schema 作答" bordered={false}>
          <SchemaRenderer
            fields={schemaVersion.schemaJson.fields}
            value={answerPayload}
            onChange={() => {}}
            readOnly
          />
        </Card>

        <AiProvenanceCard submissionId={submissionId} />
      </div>

      <AiReviewDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        result={latestResult}
        loading={triggerAiReview.isPending}
      />
    </section>
  );
}
