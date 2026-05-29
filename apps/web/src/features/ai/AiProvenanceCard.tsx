import { Button, Card, Empty, Tag, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import type { AiCall } from '../../entities/ai/aiTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { useSubmissionAiProvenanceQuery } from './useSubmissionAiProvenanceQuery';

interface AiProvenanceCardProps {
  submissionId: number;
}

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function AiProvenanceCard({ submissionId }: AiProvenanceCardProps) {
  const provenanceQuery = useSubmissionAiProvenanceQuery(submissionId, { enabled: submissionId > 0 });
  const aiCalls = provenanceQuery.data?.aiCalls ?? [];

  return (
    <Card className="ai-provenance-card" title="AI 检查记录" bordered={false}>
      <div className="ai-provenance-toolbar">
        <Typography.Text type="tertiary">共 {aiCalls.length} 次 AI 调用</Typography.Text>
        <Button
          icon={<IconRefresh />}
          size="small"
          onClick={() => provenanceQuery.refetch()}
          loading={provenanceQuery.isFetching}
        >
          刷新
        </Button>
      </div>

      {provenanceQuery.isError ? <Empty title="AI 检查记录加载失败" description="请稍后重试。" /> : null}
      {!provenanceQuery.isError && aiCalls.length === 0 ? (
        <Empty title="尚未触发 AI 检查" description="该 submission 暂无 AI 调用记录。" />
      ) : null}
      {aiCalls.length > 0 ? (
        <div className="ai-call-list">
          {aiCalls.map((call) => (
            <AiCallItem key={call.id} call={call} />
          ))}
        </div>
      ) : null}
    </Card>
  );
}

function AiCallItem({ call }: { call: AiCall }) {
  return (
    <div className="ai-call-item">
      <div className="ai-call-item__head">
        <Typography.Text strong>{call.providerName} / {call.modelName}</Typography.Text>
        <Tag color={call.status === 'completed' ? 'green' : 'red'}>{call.status}</Tag>
      </div>
      <Typography.Text type="tertiary">Prompt: {call.promptVersion}</Typography.Text>
      {call.promptVersionId != null ? (
        <Typography.Text type="tertiary">Prompt ID: #{call.promptVersionId}</Typography.Text>
      ) : null}
      {call.providerAdapterVersion ? (
        <Typography.Text type="tertiary">Adapter: {call.providerAdapterVersion}</Typography.Text>
      ) : null}
      <div className="ai-call-stat-grid">
        <AiCallStat label="Cost" value={`${call.cost ?? '-'} USD`} />
        <AiCallStat label="Latency" value={call.latencyMs != null ? `${call.latencyMs} ms` : '-'} />
        <AiCallStat label="Completed" value={formatDateTime(call.completedAt ?? call.createdAt)} />
      </div>
      <div className="ai-call-hashes">
        <span>
          input <TruncatedHash value={call.inputHash} ariaLabel={`AI call ${call.id} input hash`} />
        </span>
        <span>
          output <TruncatedHash value={call.outputHash} ariaLabel={`AI call ${call.id} output hash`} />
        </span>
      </div>
      <PromptTrace title="Business Prompt" value={call.businessPrompt} />
      <PromptTrace title="Rendered Prompt" value={call.renderedPrompt} />
    </div>
  );
}

function PromptTrace({ title, value }: { title: string; value?: string | null }) {
  if (!value) {
    return null;
  }
  return (
    <section className="ai-review-summary">
      <Typography.Text type="tertiary">{title}</Typography.Text>
      <Typography.Paragraph className="mono-value">{value}</Typography.Paragraph>
    </section>
  );
}

function AiCallStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="ai-call-stat">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Text strong>{value}</Typography.Text>
    </div>
  );
}

function formatDateTime(value?: string | null) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
