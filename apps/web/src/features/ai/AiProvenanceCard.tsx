import { Button, Card, Empty, Tag, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import type { AiCall } from '../../entities/ai/aiTypes';
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
      <Typography.Text type="tertiary">
        Cost: {call.cost ?? '-'} USD · Latency: {call.latencyMs ?? '-'} ms
      </Typography.Text>
      <Typography.Text type="tertiary">Completed: {formatDateTime(call.completedAt ?? call.createdAt)}</Typography.Text>
      <Typography.Text className="mono-value">input {shortHash(call.inputHash)} · output {shortHash(call.outputHash)}</Typography.Text>
    </div>
  );
}

function shortHash(value?: string | null) {
  return value ? `${value.slice(0, 16)}...` : '-';
}

function formatDateTime(value?: string | null) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
