import { Button, Card, Empty, Space, Spin, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconArrowLeft, IconClose, IconInfoCircle, IconPlusCircle, IconTickCircle } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD } from '../../entities/submission/answerPayload';
import {
  REVIEWER_VERDICT_LABELS,
  VERDICT_STATUS_COLORS,
  VERDICT_STATUS_LABELS,
  type QualityLedgerEntry,
  type ReviewerVerdict,
  type VerdictStatus,
} from '../../entities/quality/qualityTypes';
import { AiProvenanceCard } from '../../features/ai/AiProvenanceCard';
import { SchemaRenderer } from '../../features/labeling/SchemaRenderer';
import { useSubmissionRenderSchemaQuery } from '../../features/labeling/useSubmissionRenderSchemaQuery';
import { CreateLedgerEntryFailure, useCreateLedgerEntryMutation } from '../../features/quality/useCreateLedgerEntryMutation';
import { useLedgerEntriesQuery } from '../../features/quality/useLedgerEntriesQuery';
import { useSubmissionVerdictQuery } from '../../features/quality/useSubmissionVerdictQuery';

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function ReviewerSubmissionPage() {
  const navigate = useNavigate();
  const { submissionId: rawSubmissionId } = useParams();
  const submissionId = parseId(rawSubmissionId);
  const [reason, setReason] = useState('');
  const renderSchemaQuery = useSubmissionRenderSchemaQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const verdictQuery = useSubmissionVerdictQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const ledgerQuery = useLedgerEntriesQuery(submissionId ?? 0, { enabled: Boolean(submissionId), page: 1, size: 50 });
  const createLedgerEntry = useCreateLedgerEntryMutation();

  const renderSchema = renderSchemaQuery.data;
  const schemaVersion = renderSchema?.schemaVersion;
  const answerPayload = coerceAnswerPayload(renderSchema?.answerPayload ?? EMPTY_ANSWER_PAYLOAD);
  const ledgerEntries = ledgerQuery.data?.items ?? [];

  if (!submissionId) {
    return <Empty title="Submission 地址无效" description="请从审核队列进入 submission 详情。" />;
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
        <Empty title="Submission 不存在或无权访问" description="请确认当前 Reviewer 是否拥有审核权限。" />
        <Button onClick={() => navigate('/reviewer/submissions')}>返回审核队列</Button>
      </div>
    );
  }

  async function createVerdict(verdict: ReviewerVerdict) {
    if (!submissionId) return;
    try {
      await createLedgerEntry.mutateAsync({
        submissionId,
        entryType: 'reviewer_overall_verdict',
        payload: { verdict, reason: reason.trim() || null },
      });
      Toast.success(verdict === 'approve' ? '已通过' : '已拒绝');
      setReason('');
    } catch (error) {
      const failure = error instanceof CreateLedgerEntryFailure ? error : null;
      Toast.error(failure?.userMessage ?? '提交审核失败,请稍后重试');
    }
  }

  return (
    <section className="reviewer-submission-page" aria-label="Reviewer submission detail">
      <div className="reviewer-submission-header">
        <div>
          <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate('/reviewer/submissions')}>
            返回审核队列
          </Button>
          <Typography.Title heading={3} className="page-title">
            Submission #{submissionId}
          </Typography.Title>
          <Space wrap>
            <Tag color="purple">Schema 版本: {schemaVersionLabel(schemaVersion)}</Tag>
            <VerdictTag status={verdictQuery.data?.status ?? 'pending'} />
          </Space>
        </div>
        <div className="verdict-source-line">
          <Typography.Text>
            Verdict 来源: {verdictQuery.data?.derivedFromEntryId ? `Ledger #${verdictQuery.data.derivedFromEntryId}` : '暂无 ledger entry'}
          </Typography.Text>
          <Tooltip content="当前 Verdict 由最新 Quality Ledger entry 派生。">
            <IconInfoCircle aria-label="Verdict ledger derivation" />
          </Tooltip>
        </div>
      </div>

      <div className="reviewer-submission-grid">
        <Card className="reviewer-render-card" title="历史 Schema 作答" bordered={false}>
          <SchemaRenderer fields={schemaVersion.schemaJson.fields} value={answerPayload} onChange={() => {}} readOnly />
        </Card>

        <div className="reviewer-side-stack">
          <ReviewActionCard
            reason={reason}
            loading={createLedgerEntry.isPending}
            onReasonChange={setReason}
            onApprove={() => createVerdict('approve')}
            onReject={() => createVerdict('reject')}
          />
          <LedgerEntriesCard entries={ledgerEntries} loading={ledgerQuery.isLoading} error={ledgerQuery.isError} />
        </div>

        <AiProvenanceCard submissionId={submissionId} />
      </div>
    </section>
  );
}

function ReviewActionCard({
  reason,
  loading,
  onReasonChange,
  onApprove,
  onReject,
}: {
  reason: string;
  loading: boolean;
  onReasonChange: (value: string) => void;
  onApprove: () => void;
  onReject: () => void;
}) {
  return (
    <Card className="review-actions-card" title="审核操作" bordered={false}>
      <label className="review-reason-field">
        <Typography.Text strong>审核说明</Typography.Text>
        <textarea
          value={reason}
          onChange={(event) => onReasonChange(event.target.value)}
          placeholder="可选:补充通过或拒绝的原因"
          rows={4}
        />
      </label>
      <div className="review-actions">
        <Button className="review-approve-button" icon={<IconTickCircle />} type="tertiary" theme="solid" loading={loading} onClick={onApprove}>
          通过
        </Button>
        <Button icon={<IconClose />} type="danger" theme="solid" loading={loading} onClick={onReject}>
          拒绝
        </Button>
      </div>
    </Card>
  );
}

function LedgerEntriesCard({ entries, loading, error }: { entries: QualityLedgerEntry[]; loading: boolean; error: boolean }) {
  return (
    <Card className="ledger-entries-card" title="审核历史" bordered={false}>
      {loading ? <Spin /> : null}
      {error ? <Empty title="审核历史加载失败" description="请稍后重试。" /> : null}
      {!loading && !error && entries.length === 0 ? <Empty title="暂无审核记录" description="Reviewer 写入后会追加到 Quality Ledger。" /> : null}
      {entries.length > 0 ? (
        <div className="ledger-entry-list">
          {entries.map((entry) => (
            <LedgerEntryItem key={entry.id} entry={entry} />
          ))}
        </div>
      ) : null}
    </Card>
  );
}

function LedgerEntryItem({ entry }: { entry: QualityLedgerEntry }) {
  if (entry.entryType === 'ai_field_finding') {
    const payload = entry.payload as AiFieldFindingLedgerPayload;
    return (
      <div className="ledger-entry-item ledger-entry-item--ai">
        <div className="ledger-entry-item__head">
          <Typography.Text strong>
            <IconPlusCircle /> Ledger #{entry.id}
          </Typography.Text>
          <Space>
            <Tag color="blue" size="small">AI</Tag>
            <Tag color={severityColor(payload.severity)} size="small">{severityLabel(payload.severity)}</Tag>
          </Space>
        </div>
        <Typography.Text type="tertiary">
          AI Call <Typography.Text className="mono-value">#{entry.aiCallId ?? '-'}</Typography.Text>
          {' · '}
          {formatDateTime(entry.createdAt)}
        </Typography.Text>
        <div className="ai-finding-content">
          <Typography.Text className="mono-value">{payload.fieldPath}</Typography.Text>
          {payload.label ? <Typography.Text type="tertiary"> ({payload.label})</Typography.Text> : null}
          <Typography.Paragraph>{payload.finding}</Typography.Paragraph>
          {payload.confidence != null ? (
            <Typography.Text type="tertiary">confidence: {payload.confidence}</Typography.Text>
          ) : null}
        </div>
      </div>
    );
  }

  const payload = entry.payload as ReviewerVerdictLedgerPayload;
  const verdict = payload.verdict;
  return (
    <div className="ledger-entry-item">
      <div className="ledger-entry-item__head">
        <Typography.Text strong>
          <IconPlusCircle /> Ledger #{entry.id}
        </Typography.Text>
        <Tag color={verdict === 'approve' ? 'green' : 'red'}>{REVIEWER_VERDICT_LABELS[verdict]}</Tag>
      </div>
      <Typography.Text type="tertiary">
        {entry.actorType} #{entry.actorUserId} · {formatDateTime(entry.createdAt)}
      </Typography.Text>
      {payload.reason ? <Typography.Text>{payload.reason}</Typography.Text> : null}
    </div>
  );
}

type ReviewerVerdictLedgerPayload = Extract<NonNullable<QualityLedgerEntry['payload']>, { verdict: ReviewerVerdict }>;
type AiFieldFindingLedgerPayload = Extract<NonNullable<QualityLedgerEntry['payload']>, { fieldPath: string }>;

function severityColor(severity: AiFieldFindingLedgerPayload['severity']) {
  if (severity === 'error') return 'red';
  if (severity === 'warning') return 'orange';
  return 'blue';
}

function severityLabel(severity: AiFieldFindingLedgerPayload['severity']) {
  if (severity === 'error') return '错误';
  if (severity === 'warning') return '警告';
  return '提示';
}

function VerdictTag({ status }: { status: VerdictStatus }) {
  return <Tag color={VERDICT_STATUS_COLORS[status]}>{VERDICT_STATUS_LABELS[status]}</Tag>;
}

function parseId(value?: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
