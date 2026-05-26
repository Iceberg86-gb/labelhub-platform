import { Banner, Button, Card, Empty, Space, Spin, Tag, Typography } from '@douyinfe/semi-ui';
import { useParams } from 'react-router-dom';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD } from '../../entities/submission/answerPayload';
import { AiProvenanceCard } from '../../features/ai/AiProvenanceCard';
import { SchemaRenderer } from '../../features/labeling/SchemaRenderer';
import { SubmissionDetailFailure, useSubmissionDetailQuery } from '../../features/labeling/useSubmissionDetailQuery';
import { useSubmissionRenderSchemaQuery } from '../../features/labeling/useSubmissionRenderSchemaQuery';

function parseId(value?: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(value))
    : '—';
}

export function LabelerSubmissionPage() {
  const params = useParams();
  const submissionId = parseId(params.submissionId);
  const submissionQuery = useSubmissionDetailQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const renderSchemaQuery = useSubmissionRenderSchemaQuery(submissionId ?? 0, { enabled: submissionQuery.isSuccess });
  const submission = submissionQuery.data;
  const renderSchema = renderSchemaQuery.data;
  const schemaVersion = renderSchema?.schemaVersion;
  const answerPayload = coerceAnswerPayload(submission?.answerPayload ?? renderSchema?.answerPayload ?? EMPTY_ANSWER_PAYLOAD);

  if (!submissionId) {
    return <Empty title="Submission 地址无效" description="请从作答完成后的跳转入口进入详情页。" />;
  }

  if (submissionQuery.isLoading || renderSchemaQuery.isLoading) {
    return (
      <div className="task-state-panel">
        <Spin size="large" />
      </div>
    );
  }

  if (submissionQuery.isError || !submission) {
    const isNotFound = submissionQuery.error instanceof SubmissionDetailFailure && submissionQuery.error.status === 404;
    return (
      <Empty
        title={isNotFound ? 'Submission 不存在或无权访问' : '提交详情加载失败'}
        description={isNotFound ? '请确认当前账号是否拥有这个提交记录。' : '请稍后重试。'}
      >
        {!isNotFound ? <Button onClick={() => submissionQuery.refetch()}>重试</Button> : null}
      </Empty>
    );
  }

  if (renderSchemaQuery.isError || !schemaVersion) {
    return <Empty title="历史 Schema 加载失败" description="请稍后重试。" />;
  }

  return (
    <section className="labeler-submission-page" aria-label="Labeler submission detail">
      <div className="labeler-submission-header">
        <div>
          <Typography.Title heading={3} className="page-title">
            Submission #{submission.id}
          </Typography.Title>
          <Space wrap>
            <Tag color="green">已提交</Tag>
            <Tag color="blue">Session #{submission.sessionId}</Tag>
            <Tag color="purple">Schema 版本: {schemaVersionLabel(schemaVersion)} · 提交时绑定版本</Tag>
          </Space>
        </div>
        <Typography.Text type="tertiary">提交时间: {formatDateTime(submission.createdAt)}</Typography.Text>
      </div>

      <Card className="labeler-submission-card" bodyStyle={{ padding: 24 }}>
        <Banner
          className="submission-schema-binding-banner"
          closeIcon={null}
          description={`此 submission 按提交时绑定的 Schema v${schemaVersion.versionNumber} 渲染,历史答案不会被新 schema 重写。`}
          fullMode={false}
          type="info"
        />
        <SchemaRenderer
          fields={schemaVersion.schemaJson.fields}
          value={answerPayload}
          onChange={() => {}}
          readOnly
        />
      </Card>

      <AiProvenanceCard submissionId={submission.id} />
    </section>
  );
}
