import type { QualityLedgerEntry, VerdictStatus } from '../../entities/quality/qualityTypes';
import { FlowStrip } from '../../shared/ui';

type ReviewFlowStripProps = {
  aiOverallEntry?: QualityLedgerEntry;
  reviewerVerdictEntries: QualityLedgerEntry[];
  verdictStatus: VerdictStatus;
};

export type ReviewFlowNodeState = 'done' | 'active' | 'pending';
export type ReviewFlowNodeTone = 'success' | 'danger';

export type ReviewFlowNode = {
  key: 'submitted' | 'ai' | 'reviewer' | 'senior' | 'terminal';
  label: string;
  state: ReviewFlowNodeState;
  note?: string;
  tone?: ReviewFlowNodeTone;
};

type DeriveFlowNodesInput = {
  hasAiOverallEntry: boolean;
  reviewerVerdictEntries: QualityLedgerEntry[];
  verdictStatus: VerdictStatus;
};

export function ReviewFlowStrip({ aiOverallEntry, reviewerVerdictEntries, verdictStatus }: ReviewFlowStripProps) {
  const nodes = deriveFlowNodes({
    hasAiOverallEntry: Boolean(aiOverallEntry),
    reviewerVerdictEntries,
    verdictStatus,
  });

  return (
    <FlowStrip
      ariaLabel="Reviewer 状态流"
      className="review-flow-strip--detail"
      steps={nodes.map((node) => ({
        key: node.key,
        label: node.label,
        state: node.state,
        tone: node.tone,
        note: node.note,
      }))}
    />
  );
}

export function deriveFlowNodes({
  hasAiOverallEntry,
  reviewerVerdictEntries,
  verdictStatus,
}: DeriveFlowNodesInput): ReviewFlowNode[] {
  const hasReviewerEntry = reviewerVerdictEntries.some((entry) => isReviewerLevelEntry(entry, 'reviewer'));
  const hasSeniorEntry = reviewerVerdictEntries.some((entry) => isReviewerLevelEntry(entry, 'senior_reviewer'));
  const seniorSkipped = verdictStatus === 'rejected' && !hasSeniorEntry;

  return [
    { key: 'submitted', label: '提交', state: 'done' },
    {
      key: 'ai',
      label: 'AI 预审',
      state: hasAiOverallEntry ? 'done' : hasReviewerEntry ? 'pending' : 'active',
      note: !hasAiOverallEntry && hasReviewerEntry ? '未启用' : undefined,
    },
    {
      key: 'reviewer',
      label: '初审',
      state: hasReviewerEntry ? 'done' : hasAiOverallEntry ? 'active' : 'pending',
    },
    {
      key: 'senior',
      label: '复核/终审',
      state: hasSeniorEntry ? 'done' : seniorSkipped ? 'pending' : hasReviewerEntry ? 'active' : 'pending',
      note: seniorSkipped ? '跳过' : undefined,
    },
    terminalNode(verdictStatus),
  ];
}

function terminalNode(verdictStatus: VerdictStatus): ReviewFlowNode {
  if (verdictStatus === 'approved') {
    return { key: 'terminal', label: '通过 · 可入库/可导出', state: 'done', tone: 'success' };
  }
  if (verdictStatus === 'rejected') {
    return { key: 'terminal', label: '打回', state: 'done', tone: 'danger' };
  }
  return { key: 'terminal', label: '待裁决', state: 'pending' };
}

function isReviewerLevelEntry(entry: QualityLedgerEntry, reviewLevel: 'reviewer' | 'senior_reviewer') {
  return entry.entryType === 'reviewer_overall_verdict'
    && 'reviewLevel' in entry.payload
    && entry.payload.reviewLevel === reviewLevel;
}
