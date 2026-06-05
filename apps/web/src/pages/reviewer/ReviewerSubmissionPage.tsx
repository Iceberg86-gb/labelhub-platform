import { Button, Card, Empty, Space, Spin, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconArrowLeft, IconClose, IconInfoCircle, IconTickCircle } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { schemaFields } from '../../entities/schema/runtimeSchema';
import { schemaVersionLabel } from '../../entities/schema/schemaTypes';
import { coerceAnswerPayload, EMPTY_ANSWER_PAYLOAD } from '../../entities/submission/answerPayload';
import {
  REVIEWER_VERDICT_LABELS,
  REVIEW_LEVEL_LABELS,
  VERDICT_STATUS_LABELS,
  type QualityLedgerEntry,
  type ReviewLevel,
  type ReviewerVerdict,
  type Verdict,
  type VerdictStatus,
} from '../../entities/quality/qualityTypes';
import { AiProvenanceCard } from '../../features/ai/AiProvenanceCard';
import { useSubmissionRenderSchemaQuery } from '../../features/labeling/useSubmissionRenderSchemaQuery';
import { CreateLedgerEntryFailure, useCreateLedgerEntryMutation } from '../../features/quality/useCreateLedgerEntryMutation';
import { useLedgerEntriesQuery } from '../../features/quality/useLedgerEntriesQuery';
import { useSubmissionVerdictQuery } from '../../features/quality/useSubmissionVerdictQuery';
import { getUser } from '../../shared/api/auth-storage';
import { ReviewerAnswerSummary } from './ReviewerAnswerSummary';
import { ReviewFlowStrip } from './ReviewFlowStrip';

// ReviewerAnswerSummary is this page's read-only replacement for the previous SchemaFormilyRenderer consumer.
const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function ReviewerSubmissionPage() {
  const navigate = useNavigate();
  const { submissionId: rawSubmissionId } = useParams();
  const [searchParams] = useSearchParams();
  const submissionId = parseId(rawSubmissionId);
  const currentUser = getUser();
  const reviewLevel = parseReviewLevel(searchParams.get('reviewLevel'))
    ?? (currentUser?.roles.includes('SENIOR_REVIEWER') ? 'senior_reviewer' : 'reviewer');
  const [reason, setReason] = useState('');
  const renderSchemaQuery = useSubmissionRenderSchemaQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const verdictQuery = useSubmissionVerdictQuery(submissionId ?? 0, { enabled: Boolean(submissionId) });
  const ledgerQuery = useLedgerEntriesQuery(submissionId ?? 0, { enabled: Boolean(submissionId), page: 1, size: 50 });
  const createLedgerEntry = useCreateLedgerEntryMutation();

  const renderSchema = renderSchemaQuery.data;
  const schemaVersion = renderSchema?.schemaVersion;
  const answerPayload = coerceAnswerPayload(renderSchema?.answerPayload ?? EMPTY_ANSWER_PAYLOAD);
  const ledgerEntries = ledgerQuery.data?.items ?? [];
  const aiOverallEntry = latestEntry(ledgerEntries, isAiOverallRecommendationEntry);
  const aiFindingEntries = ledgerEntries.filter(isAiFieldFindingEntry);
  const reviewerVerdictEntries = ledgerEntries.filter(isReviewerVerdictEntry);

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
    if (verdict === 'reject' && !reason.trim()) {
      Toast.warning('打回必须填写理由');
      return;
    }
    try {
      await createLedgerEntry.mutateAsync({
        submissionId,
        entryType: 'reviewer_overall_verdict',
        payload: { verdict, reviewLevel, reason: reason.trim() || null },
      });
      Toast.success(successMessage(verdict, reviewLevel));
      setReason('');
    } catch (error) {
      const failure = error instanceof CreateLedgerEntryFailure ? error : null;
      Toast.error(failure?.userMessage ?? '提交审核失败,请稍后重试');
    }
  }

  const reviewerSchemaFields = schemaFields(schemaVersion.schemaJson);

  return (
    <section className="reviewer-submission-page reviewer-submission-page--decision" aria-label="Reviewer submission detail">
      <header className="reviewer-submission-header reviewer-decision-hero">
        <div className="reviewer-decision-hero__copy">
          <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate(`/reviewer/submissions?reviewLevel=${reviewLevel}`)}>
            返回审核队列
          </Button>
          <Typography.Title heading={3} className="page-title">
            Submission #{submissionId}
          </Typography.Title>
          <Space wrap>
            <Tag className="reviewer-schema-tag">Schema 版本: {schemaVersionLabel(schemaVersion)}</Tag>
            <ReviewLevelTag reviewLevel={reviewLevel} />
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
      </header>

      {!ledgerQuery.isLoading && !ledgerQuery.isError ? (
        <ReviewFlowStrip aiOverallEntry={aiOverallEntry} reviewerVerdictEntries={reviewerVerdictEntries} verdictStatus={verdictQuery.data?.status ?? 'pending'} />
      ) : null}
      <AiRecommendationCard overallEntry={aiOverallEntry} ledgerLoading={ledgerQuery.isLoading} ledgerError={ledgerQuery.isError} />

      <div className="reviewer-workbench-grid">
        <Card className="reviewer-render-card reviewer-reading-panel reviewer-comparison-panel" title="题目原文与标注员答案" bordered={false}>
          <ReviewerAnswerSummary
            schemaFields={reviewerSchemaFields}
            answerPayload={answerPayload}
            itemPayload={renderSchema.datasetItem?.itemPayload}
          />
        </Card>

        <aside className="reviewer-human-decision-panel reviewer-workbench-rail" aria-label="人工最终裁决与 AI 证据">
          <div className="reviewer-decision-rail__sticky">
            <div className="reviewer-decision-rail">
              <ReviewActionCard
                reason={reason}
                loading={createLedgerEntry.isPending}
                onReasonChange={setReason}
                onApprove={() => createVerdict('approve')}
                onReject={() => createVerdict('reject')}
                reviewLevel={reviewLevel}
              />
              <HumanFinalVerdictCard verdict={verdictQuery.data ?? null} entries={reviewerVerdictEntries} />
            </div>
          </div>
          <details className="reviewer-ai-layer reviewer-ai-layer--fields">
            <summary>字段级发现</summary>
            <AiFieldFindingList entries={aiFindingEntries} />
          </details>
          <details className="reviewer-ai-layer reviewer-ai-layer--debug">
            <summary>AI 调用详情(调试)</summary>
            <AiProvenanceCard submissionId={submissionId} className="ai-provenance-card--assistive" />
          </details>
          <LedgerEntriesCard entries={ledgerEntries} loading={ledgerQuery.isLoading} error={ledgerQuery.isError} />
        </aside>
      </div>
    </section>
  );
}

function AiRecommendationCard({
  overallEntry,
  ledgerLoading,
  ledgerError,
}: {
  overallEntry?: QualityLedgerEntry;
  ledgerLoading: boolean;
  ledgerError: boolean;
}) {
  if (ledgerLoading) {
    return (
      <Card className="ai-recommendation-card ai-recommendation-card--pending reviewer-ai-summary-band" title="AI 综合判定（建议）" bordered={false}>
        <div className="reviewer-ai-summary-band__metrics">
          <Typography.Text type="tertiary">正在读取 AI 预审证据。</Typography.Text>
        </div>
      </Card>
    );
  }

  if (ledgerError) {
    return (
      <Card className="ai-recommendation-card ai-recommendation-card--pending reviewer-ai-summary-band" title="AI 综合判定（建议）" bordered={false}>
        <div className="reviewer-ai-summary-band__metrics">
          <Typography.Text type="tertiary">AI 建议加载失败,请稍后重试。</Typography.Text>
        </div>
      </Card>
    );
  }

  if (!overallEntry) {
    return (
      <Card className="ai-recommendation-card ai-recommendation-card--pending reviewer-ai-summary-band" title="AI 综合判定（建议）" bordered={false}>
        <div className="reviewer-ai-summary-band__metrics">
          <Typography.Text type="tertiary">预审未完成时不显示判定结论。AI 只提供建议证据,非最终裁决。</Typography.Text>
        </div>
      </Card>
    );
  }

  const payload = overallEntry.payload as AiOverallRecommendationLedgerPayload;
  const dimensionScores = payload.dimensionScores ?? [];

  return (
    <Card className="ai-recommendation-card reviewer-ai-overview-card reviewer-ai-summary-band" title="AI 综合判定（建议）" bordered={false}>
      <div className="reviewer-ai-summary-band__metrics reviewer-ai-recommendation-line">
        <Typography.Text type="tertiary">AI 建议 · 非最终裁决</Typography.Text>
        <Tag className={`semantic-tag semantic-tag--${aiRecommendationTone(payload.recommendation)}`}>
          {AI_RECOMMENDATION_LABELS[payload.recommendation]}
        </Tag>
        <strong className="reviewer-ai-score">{formatDecimal(payload.finalScore)}</strong>
        <span>
          （通过阈值 {formatOptionalDecimal(payload.passThreshold ?? payload.threshold)}，拒绝阈值{' '}
          {formatOptionalDecimal(payload.rejectThreshold ?? payload.rejectFloor)}）
        </span>
      </div>

      {payload.summary ? <Typography.Paragraph className="ai-recommendation-summary">{payload.summary}</Typography.Paragraph> : null}

      {dimensionScores.length > 0 ? (
        <div className="reviewer-ai-dimension-mini-bars" aria-label="AI dimension scores">
          {dimensionScores.map((dimension) => (
            <div className="reviewer-ai-dimension-mini-bar" key={dimension.dimension}>
              <div className="reviewer-ai-dimension-mini-bar__head">
                <span className="ai-dimension-score__name">{formatDimensionLabel(dimension.dimension)}</span>
                <span className="ai-dimension-score__value">{formatDecimal(dimension.score)}</span>
              </div>
              <span className="reviewer-ai-dimension-mini-bar__track">
                <span style={{ width: `${scorePercent(dimension.score)}%` }} />
              </span>
            </div>
          ))}
        </div>
      ) : null}
    </Card>
  );
}

function AiFieldFindingList({ entries }: { entries: QualityLedgerEntry[] }) {
  if (!entries.length) {
    return <Typography.Text type="tertiary">暂无字段级发现。</Typography.Text>;
  }

  return (
    <div className="reviewer-ai-finding-list reviewer-ai-finding-list--compact" aria-label="AI field findings">
      {entries.map((entry) => {
        const finding = entry.payload as AiFieldFindingLedgerPayload;
        return (
          <div className="ai-field-finding-pill" key={entry.id}>
            <Tag className={`semantic-tag semantic-tag--${severityTone(finding.severity)}`} size="small">{severityLabel(finding.severity)}</Tag>
            <Typography.Text className="mono-value">{formatFieldPathLabel(finding.fieldPath)}</Typography.Text>
            <Typography.Text>{finding.finding}</Typography.Text>
          </div>
        );
      })}
    </div>
  );
}

function HumanFinalVerdictCard({ verdict, entries }: { verdict: Verdict | null; entries: QualityLedgerEntry[] }) {
  const latestReviewerEntry = entries[0];

  return (
    <Card className="human-final-verdict-card" title="人工最终裁决" bordered={false}>
      <div className="human-final-verdict-card__status">
        <VerdictTag status={verdict?.status ?? 'pending'} />
        <Typography.Text type="tertiary">最终裁决来源: {verdict?.derivedFromEntryId ? `Ledger #${verdict.derivedFromEntryId}` : '暂无人工 ledger entry'}</Typography.Text>
      </div>
      {latestReviewerEntry ? (
        <div className="human-final-verdict-card__ledger">
          <Typography.Text type="tertiary">最近人工记录</Typography.Text>
          <ReviewerLedgerSummary entry={latestReviewerEntry} />
        </div>
      ) : (
        <Typography.Text type="tertiary">等待 Reviewer 或 Senior Reviewer 写入人工裁决。</Typography.Text>
      )}
    </Card>
  );
}

function ReviewerLedgerSummary({ entry }: { entry: QualityLedgerEntry }) {
  const payload = entry.payload as ReviewerVerdictLedgerPayload;
  return (
    <div className="human-final-verdict-card__summary">
      <Tag className={`semantic-tag semantic-tag--${payload.verdict === 'approve' ? 'success' : 'danger'}`}>
        {REVIEWER_VERDICT_LABELS[payload.verdict]}
      </Tag>
      <Typography.Text>{REVIEW_LEVEL_LABELS[payload.reviewLevel]} · {entry.actorType} #{entry.actorUserId ?? '-'}</Typography.Text>
      {payload.reason ? <Typography.Text type="tertiary">{payload.reason}</Typography.Text> : null}
    </div>
  );
}

function ReviewActionCard({
  reason,
  loading,
  onReasonChange,
  onApprove,
  onReject,
  reviewLevel,
}: {
  reason: string;
  loading: boolean;
  onReasonChange: (value: string) => void;
  onApprove: () => void;
  onReject: () => void;
  reviewLevel: ReviewLevel;
}) {
  return (
    <Card className="review-actions-card review-actions-card--primary" title={`人工最终裁决 · ${REVIEW_LEVEL_LABELS[reviewLevel]}操作`} bordered={false}>
      <label className="review-reason-field review-reason-field--required">
        <Typography.Text strong>审核说明</Typography.Text>
        <textarea
          value={reason}
          onChange={(event) => onReasonChange(event.target.value)}
          placeholder="通过可选说明;打回必须填写理由"
          rows={4}
        />
      </label>
      <div className="review-actions">
        <Button className="review-approve-button" icon={<IconTickCircle />} type="tertiary" theme="solid" loading={loading} onClick={onApprove}>
          {reviewLevel === 'senior_reviewer' ? '复核通过' : '初审通过'}
        </Button>
        <Button icon={<IconClose />} type="danger" theme="solid" loading={loading} onClick={onReject}>
          打回
        </Button>
      </div>
    </Card>
  );
}

function LedgerEntriesCard({ entries, loading, error }: { entries: QualityLedgerEntry[]; loading: boolean; error: boolean }) {
  return (
    <details className="reviewer-ai-layer reviewer-ai-layer--history ledger-entries-card">
      <summary>审核历史</summary>
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
    </details>
  );
}

function LedgerEntryItem({ entry }: { entry: QualityLedgerEntry }) {
  if (entry.entryType === 'ai_field_finding') {
    const payload = entry.payload as AiFieldFindingLedgerPayload;
    return (
      <div className="ledger-entry-item ledger-entry-item--ai ledger-entry-timeline">
        <time>{formatDateTime(entry.createdAt)}</time>
        <Typography.Text className="mono-value">{formatFieldPathLabel(payload.fieldPath)}</Typography.Text>
        <span>
          <Tag className={`semantic-tag semantic-tag--${severityTone(payload.severity)}`} size="small">{severityLabel(payload.severity)}</Tag>
          {payload.finding}
        </span>
        <Typography.Text type="tertiary">置信度 {payload.confidence ?? '-'}</Typography.Text>
      </div>
    );
  }

  if (entry.entryType === 'ai_overall_recommendation') {
    const payload = entry.payload as AiOverallRecommendationLedgerPayload;
    return (
      <div className="ledger-entry-item ledger-entry-item--ai ledger-entry-timeline">
        <time>{formatDateTime(entry.createdAt)}</time>
        <Typography.Text className="mono-value">AI 综合</Typography.Text>
        <span>
          <Tag className={`semantic-tag semantic-tag--${aiRecommendationTone(payload.recommendation)}`} size="small">
            {AI_RECOMMENDATION_LABELS[payload.recommendation]}
          </Tag>
          {payload.summary ?? '综合建议'}
        </span>
        <Typography.Text type="tertiary">置信度 {formatDecimal(payload.finalScore)}</Typography.Text>
      </div>
    );
  }

  const payload = entry.payload as ReviewerVerdictLedgerPayload;
  const verdict = payload.verdict;
  return (
    <div className="ledger-entry-item ledger-entry-timeline">
      <time>{formatDateTime(entry.createdAt)}</time>
      <Typography.Text className="mono-value">{REVIEW_LEVEL_LABELS[payload.reviewLevel]}</Typography.Text>
      <span>
        <Tag className={`semantic-tag semantic-tag--${verdict === 'approve' ? 'success' : 'danger'}`}>{REVIEWER_VERDICT_LABELS[verdict]}</Tag>
        {payload.reason ?? '无说明'}
      </span>
      <Typography.Text type="tertiary">{entry.actorType} #{entry.actorUserId}</Typography.Text>
    </div>
  );
}

type ReviewerVerdictLedgerPayload = Extract<NonNullable<QualityLedgerEntry['payload']>, { verdict: ReviewerVerdict }>;
type AiFieldFindingLedgerPayload = Extract<NonNullable<QualityLedgerEntry['payload']>, { fieldPath: string }>;
type AiOverallRecommendationLedgerPayload = Extract<NonNullable<QualityLedgerEntry['payload']>, { recommendation: 'pass' | 'reject' | 'manual_review' }>;

const AI_RECOMMENDATION_LABELS: Record<AiOverallRecommendationLedgerPayload['recommendation'], string> = {
  pass: '建议通过',
  reject: '建议拒绝',
  manual_review: '建议人工复核',
};

const AI_DIMENSION_LABELS: Record<string, string> = {
  relevance: '相关性(relevance)',
  relevance_score: '相关性(relevance)',
  accuracy: '准确性(accuracy)',
  accuracy_score: '准确性(accuracy)',
  format: '表达与格式(format)',
  format_score: '表达与格式(format)',
  safety: '安全性(safety)',
  safety_score: '安全性(safety)',
};

function latestEntry<T extends QualityLedgerEntry>(
  entries: QualityLedgerEntry[],
  predicate: (entry: QualityLedgerEntry) => entry is T,
) {
  return entries.find(predicate);
}

function isAiOverallRecommendationEntry(entry: QualityLedgerEntry): entry is QualityLedgerEntry {
  return entry.entryType === 'ai_overall_recommendation';
}

function isAiFieldFindingEntry(entry: QualityLedgerEntry): entry is QualityLedgerEntry {
  return entry.entryType === 'ai_field_finding';
}

function isReviewerVerdictEntry(entry: QualityLedgerEntry): entry is QualityLedgerEntry {
  return entry.entryType === 'reviewer_overall_verdict';
}

function aiRecommendationTone(recommendation: AiOverallRecommendationLedgerPayload['recommendation']) {
  if (recommendation === 'pass') return 'success';
  if (recommendation === 'reject') return 'danger';
  return 'warning';
}

function formatDecimal(value?: string | number | null) {
  if (value == null || value === '') return '-';
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return numeric.toFixed(2);
}

function formatOptionalDecimal(value?: string | number | null) {
  return value == null || value === '' ? '-' : formatDecimal(value);
}

function scorePercent(value?: string | number | null) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return 0;
  return Math.max(0, Math.min(100, numeric * 100));
}

function formatDimensionLabel(dimension: string) {
  return AI_DIMENSION_LABELS[dimension] ?? dimension;
}

function formatFieldPathLabel(fieldPath: string) {
  return AI_DIMENSION_LABELS[fieldPath] ?? fieldPath;
}

function severityTone(severity: AiFieldFindingLedgerPayload['severity']) {
  if (severity === 'error') return 'danger';
  if (severity === 'warning') return 'warning';
  return 'accent';
}

function severityLabel(severity: AiFieldFindingLedgerPayload['severity']) {
  if (severity === 'error') return '错误';
  if (severity === 'warning') return '警告';
  return '提示';
}

function VerdictTag({ status }: { status: VerdictStatus }) {
  return <Tag className={`semantic-tag semantic-tag--${verdictStatusTone(status)}`}>{VERDICT_STATUS_LABELS[status]}</Tag>;
}

function verdictStatusTone(status: VerdictStatus) {
  if (status === 'approved') return 'success';
  if (status === 'rejected') return 'danger';
  return 'warning';
}

function ReviewLevelTag({ reviewLevel }: { reviewLevel: ReviewLevel }) {
  return (
    <Tag className={`reviewer-level-tag reviewer-level-tag--${reviewLevel === 'senior_reviewer' ? 'senior' : 'initial'}`}>
      {REVIEW_LEVEL_LABELS[reviewLevel]}
    </Tag>
  );
}

function parseId(value?: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function parseReviewLevel(value: string | null): ReviewLevel | undefined {
  return value === 'reviewer' || value === 'senior_reviewer' ? value : undefined;
}

function successMessage(verdict: ReviewerVerdict, reviewLevel: ReviewLevel) {
  if (verdict === 'reject') return '已打回';
  return reviewLevel === 'senior_reviewer' ? '复核已通过' : '初审已通过,已进入复核队列';
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
