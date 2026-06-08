import { Button, Card, Empty, Tag, Typography } from '@douyinfe/semi-ui';
import { IconChevronDown, IconChevronUp, IconRefresh } from '@douyinfe/semi-icons';
import { type ReactNode, useState } from 'react';
import type { AiCall } from '../../entities/ai/aiTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { useSubmissionAiProvenanceQuery } from './useSubmissionAiProvenanceQuery';

interface AiProvenanceCardProps {
  className?: string;
  submissionId: number;
}

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const AI_CALL_STATUS_LABELS: Record<AiCall['status'], string> = {
  completed: '已完成',
  failed: '失败',
};

const AI_CALL_STATUS_TONES: Record<AiCall['status'], 'success' | 'danger' | 'warning' | 'info'> = {
  completed: 'success',
  failed: 'danger',
};

export function AiProvenanceCard({ className, submissionId }: AiProvenanceCardProps) {
  const provenanceQuery = useSubmissionAiProvenanceQuery(submissionId, { enabled: submissionId > 0 });
  const aiCalls = provenanceQuery.data?.aiCalls ?? [];

  return (
    <Card className={['ai-provenance-card', className].filter(Boolean).join(' ')} title="AI 预审记录" bordered={false}>
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

      {provenanceQuery.isError ? <Empty title="AI 预审记录加载失败" description="请稍后重试。" /> : null}
      {!provenanceQuery.isError && aiCalls.length === 0 ? (
        <Empty title="尚未发起 AI 预审" description="该 submission 暂无 AI 调用记录。" />
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
        <div className="ai-call-item__title">
          <Typography.Text strong>AI 调用 #{call.id}</Typography.Text>
          <Typography.Text type="tertiary">{call.providerName} / {call.modelName}</Typography.Text>
        </div>
        <Tag className={`semantic-tag semantic-tag--${AI_CALL_STATUS_TONES[call.status]}`}>{AI_CALL_STATUS_LABELS[call.status]}</Tag>
      </div>
      <div className="ai-call-summary-grid">
        <div>
          <Typography.Text type="tertiary">提示词版本</Typography.Text>
          <Typography.Text strong>{call.promptVersionId != null ? `#${call.promptVersionId}` : call.promptVersion}</Typography.Text>
        </div>
        <div>
          <Typography.Text type="tertiary">模型</Typography.Text>
          <Typography.Text strong>{call.modelName}</Typography.Text>
        </div>
      </div>
      <div className="ai-call-stat-grid">
        <AiCallStat label="费用" value={`${call.cost ?? '-'} USD`} />
        <AiCallStat label="耗时" value={call.latencyMs != null ? `${call.latencyMs} ms` : '-'} />
        <AiCallStat label="完成时间" value={formatDateTime(call.completedAt ?? call.createdAt)} />
      </div>
      <PromptEvidence call={call} />
      <TechnicalEvidence call={call} />
    </div>
  );
}

function PromptEvidence({ call }: { call: AiCall }) {
  const traces = [
    { title: '业务规则提示词', value: call.businessPrompt },
    { title: '最终发送提示词', value: call.renderedPrompt },
  ].filter((trace): trace is { title: string; value: string } => Boolean(trace.value));

  if (traces.length === 0) {
    return null;
  }

  const totalCharacters = traces.reduce((total, trace) => total + trace.value.length, 0);

  return (
    <AiCallDisclosure
      bodyId={`ai-call-${call.id}-prompt-evidence`}
      title="提示词证据"
      collapsedText={`${traces.length} 段提示词，共 ${totalCharacters} 字符。展开查看原始内容。`}
    >
      <div className="ai-call-trace-list">
        {traces.map((trace) => (
          <div className="ai-call-trace" key={trace.title}>
            <Typography.Text type="tertiary">{trace.title}</Typography.Text>
            <Typography.Paragraph className="mono-value ai-review-summary__body">
              {trace.value}
            </Typography.Paragraph>
          </div>
        ))}
      </div>
    </AiCallDisclosure>
  );
}

function TechnicalEvidence({ call }: { call: AiCall }) {
  return (
    <AiCallDisclosure
      bodyId={`ai-call-${call.id}-technical-evidence`}
      title="技术指纹与适配器"
      collapsedText="输入/输出指纹、适配器版本等排查信息。"
    >
      <div className="ai-call-technical-grid">
        <TechnicalItem label="适配器版本" value={call.providerAdapterVersion} />
        <TechnicalItem label="原始提示词版本" value={call.promptVersion} />
        <TechnicalItem label="输入指纹" value={<TruncatedHash value={call.inputHash} ariaLabel={`AI call ${call.id} input hash`} />} />
        <TechnicalItem label="输出指纹" value={<TruncatedHash value={call.outputHash} ariaLabel={`AI call ${call.id} output hash`} />} />
      </div>
    </AiCallDisclosure>
  );
}

function AiCallDisclosure({
  bodyId,
  children,
  collapsedText,
  title,
}: {
  bodyId: string;
  children: ReactNode;
  collapsedText: string;
  title: string;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <section className={`ai-review-summary ai-review-summary--trace${expanded ? ' ai-review-summary--expanded' : ''}`}>
      <div className="ai-review-summary__head">
        <Typography.Text type="tertiary">{title}</Typography.Text>
        <Button
          aria-controls={bodyId}
          aria-expanded={expanded}
          icon={expanded ? <IconChevronUp /> : <IconChevronDown />}
          size="small"
          theme="borderless"
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? '收起' : '展开'}
        </Button>
      </div>
      {expanded ? (
        <div id={bodyId}>
          {children}
        </div>
      ) : (
        <Typography.Text type="tertiary" className="ai-review-summary__hint">
          {collapsedText}
        </Typography.Text>
      )}
    </section>
  );
}

function TechnicalItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="ai-call-technical-item">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Text strong>{value ?? '-'}</Typography.Text>
    </div>
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
