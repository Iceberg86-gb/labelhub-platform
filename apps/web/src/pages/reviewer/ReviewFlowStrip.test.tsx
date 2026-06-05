import { describe, expect, it } from 'vitest';
import type { QualityLedgerEntry } from '../../entities/quality/qualityTypes';
import { deriveFlowNodes } from './ReviewFlowStrip';

describe('deriveFlowNodes', () => {
  it('marks every review step done for an approved senior-review chain', () => {
    expect(snapshotNodes({
      hasAiOverallEntry: true,
      reviewerVerdictEntries: [
        reviewerEntry(2, 'senior_reviewer', 'approve'),
        reviewerEntry(1, 'reviewer', 'approve'),
      ],
      verdictStatus: 'approved',
    })).toEqual([
      ['提交', 'done'],
      ['AI 预审', 'done'],
      ['初审', 'done'],
      ['复核/终审', 'done'],
      ['通过 · 可入库/可导出', 'done', 'success'],
    ]);
  });

  it('marks senior review skipped when an initial review rejects directly', () => {
    expect(snapshotNodes({
      hasAiOverallEntry: true,
      reviewerVerdictEntries: [reviewerEntry(1, 'reviewer', 'reject')],
      verdictStatus: 'rejected',
    })).toEqual([
      ['提交', 'done'],
      ['AI 预审', 'done'],
      ['初审', 'done'],
      ['复核/终审', 'pending', undefined, '跳过'],
      ['打回', 'done', 'danger'],
    ]);
  });

  it('keeps AI pre-review pending with an unconfigured note after reviewer progress exists', () => {
    expect(snapshotNodes({
      hasAiOverallEntry: false,
      reviewerVerdictEntries: [reviewerEntry(1, 'reviewer', 'approve')],
      verdictStatus: 'pending',
    })).toEqual([
      ['提交', 'done'],
      ['AI 预审', 'pending', undefined, '未启用'],
      ['初审', 'done'],
      ['复核/终审', 'active'],
      ['待裁决', 'pending'],
    ]);
  });

  it('activates the first unfinished step while the submission is pending', () => {
    expect(snapshotNodes({
      hasAiOverallEntry: true,
      reviewerVerdictEntries: [],
      verdictStatus: 'pending',
    })).toEqual([
      ['提交', 'done'],
      ['AI 预审', 'done'],
      ['初审', 'active'],
      ['复核/终审', 'pending'],
      ['待裁决', 'pending'],
    ]);
  });
});

function snapshotNodes(input: Parameters<typeof deriveFlowNodes>[0]) {
  return deriveFlowNodes(input).map((node) => {
    const snapshot = [node.label, node.state, node.tone, node.note];
    while (snapshot[snapshot.length - 1] === undefined) {
      snapshot.pop();
    }
    return snapshot;
  });
}

function reviewerEntry(
  id: number,
  reviewLevel: 'reviewer' | 'senior_reviewer',
  verdict: 'approve' | 'reject',
): QualityLedgerEntry {
  return {
    id,
    submissionId: 501,
    taskId: 22,
    entryType: 'reviewer_overall_verdict',
    actorType: 'user',
    actorUserId: 1003,
    aiCallId: null,
    payload: { verdict, reviewLevel, reason: null },
    createdAt: '2026-06-04T00:00:00Z',
  };
}
