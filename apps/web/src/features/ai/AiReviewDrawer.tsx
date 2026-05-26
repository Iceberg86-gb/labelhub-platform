import { Banner, Empty, SideSheet, Spin, Tag, Typography } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';
import type { AiReviewResult, FieldFinding } from '../../entities/ai/aiTypes';
import { OVERALL_SUGGESTION_LABELS, SEVERITY_COLORS, SEVERITY_LABELS } from '../../entities/ai/aiTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';

type AiReviewDrawerProps = {
  open: boolean;
  onClose: () => void;
  result: AiReviewResult | null;
  loading: boolean;
};

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function AiReviewDrawer({ open, onClose, result, loading }: AiReviewDrawerProps) {
  return (
    <SideSheet title="AI 检查结果" visible={open} width={640} onCancel={onClose}>
      <div className="ai-review-drawer">
        {loading ? (
          <div className="ai-review-state">
            <Spin size="large" />
            <Typography.Text type="tertiary">AI 正在检查 submission...</Typography.Text>
          </div>
        ) : null}

        {!loading && !result ? (
          <Empty title="暂无本次检查结果" description="触发 AI 检查后,结果会显示在这里。" />
        ) : null}

        {!loading && result ? <AiReviewResultPanel result={result} /> : null}
      </div>
    </SideSheet>
  );
}

function AiReviewResultPanel({ result }: { result: AiReviewResult }) {
  const aiCall = result.aiCall;
  const completedAt = aiCall.completedAt ?? aiCall.createdAt;
  const costText = aiCall.cost ? `${aiCall.cost} USD` : '-';
  const latencyText = aiCall.latencyMs != null ? `${aiCall.latencyMs} ms` : '-';
  const completedText = formatDateTime(completedAt);

  return (
    <>
      <Banner
        className={result.idempotencyHit ? 'ai-review-banner ai-review-banner--hit' : 'ai-review-banner'}
        fullMode={false}
        type={result.idempotencyHit ? 'info' : 'success'}
        closeIcon={null}
        title={result.idempotencyHit ? '复用历史结果' : 'AI 检查完成'}
        description={
          result.idempotencyHit
            ? `input hash 与 idempotency key 命中,本次未产生 cost(命中历史 ${costText})。`
            : 'Mock provider 已完成本次字段级检查。'
        }
      />

      <div className="ai-review-stat-grid">
        <ReviewStat label="Cost" value={costText} />
        <ReviewStat label="Latency" value={latencyText} />
        <ReviewStat label="Completed" value={completedText} />
      </div>

      <div className="ai-review-meta-grid">
        <MetaItem label="AI 建议" value={<Tag color="green">{OVERALL_SUGGESTION_LABELS[result.overallSuggestion]}</Tag>} />
        <MetaItem label="Provider" value={`${aiCall.providerName} / ${aiCall.modelName}`} />
        <MetaItem label="Prompt" value={aiCall.promptVersion} />
        <MetaItem label="Input hash" value={<TruncatedHash value={aiCall.inputHash} ariaLabel={`AI review ${aiCall.id} input hash`} />} />
        <MetaItem label="Output hash" value={<TruncatedHash value={aiCall.outputHash} ariaLabel={`AI review ${aiCall.id} output hash`} />} />
        {result.usage ? (
          <>
            <MetaItem label="Prompt Tokens" value={result.usage.promptTokens ?? '-'} />
            <MetaItem label="Completion Tokens" value={result.usage.completionTokens ?? '-'} />
            <MetaItem label="Total Tokens" value={result.usage.totalTokens ?? '-'} />
            {result.usage.cacheHitTokens != null ? (
              <MetaItem label="Cache Hit Tokens" value={result.usage.cacheHitTokens} />
            ) : null}
          </>
        ) : null}
      </div>

      <section className="ai-review-summary">
        <Typography.Text strong>Summary</Typography.Text>
        <Typography.Paragraph>{result.summary || 'AI 未返回概要。'}</Typography.Paragraph>
      </section>

      <section className="ai-finding-list" aria-label="AI field findings">
        <Typography.Text strong>字段反馈</Typography.Text>
        {result.fieldFindings.length === 0 ? (
          <Empty title="无字段反馈" description="AI 未对任何字段提供反馈。" />
        ) : (
          result.fieldFindings.map((finding) => <FindingItem key={`${finding.fieldPath}-${finding.finding}`} finding={finding} />)
        )}
      </section>
    </>
  );
}

function ReviewStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="ai-review-stat">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Text strong>{value}</Typography.Text>
    </div>
  );
}

function MetaItem({ label, value, mono = false }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div>
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Text className={mono ? 'mono-value' : undefined} strong>
        {value}
      </Typography.Text>
    </div>
  );
}

function FindingItem({ finding }: { finding: FieldFinding }) {
  return (
    <div className="ai-finding-item">
      <div className="ai-finding-item__head">
        <Typography.Text className="mono-value" strong>{finding.fieldPath}</Typography.Text>
        <Tag color={SEVERITY_COLORS[finding.severity]}>{SEVERITY_LABELS[finding.severity]}</Tag>
      </div>
      {finding.label ? <Typography.Text type="tertiary">{finding.label}</Typography.Text> : null}
      <Typography.Paragraph>{finding.finding}</Typography.Paragraph>
      {finding.confidence ? <Typography.Text type="tertiary">confidence: {finding.confidence}</Typography.Text> : null}
    </div>
  );
}

function formatDateTime(value?: string | null) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
