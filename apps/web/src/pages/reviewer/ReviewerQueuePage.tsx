import { Button, Empty, Modal, Pagination, Select, Space, Spin, Table, Tag, TextArea, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconInfoCircle, IconPlay, IconRefresh } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  VERDICT_STATUS_LABELS,
  REVIEW_LEVEL_LABELS,
  type ReviewLevel,
  type ReviewerSubmissionSummary,
  type VerdictStatus,
} from '../../entities/quality/qualityTypes';
import { useReviewerQueueQuery } from '../../features/quality/useReviewerQueueQuery';
import { useBatchReviewMutation } from '../../features/quality/useBatchReviewMutation';
import { getUser } from '../../shared/api/auth-storage';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;
const VERDICT_FILTERS: VerdictStatus[] = ['pending', 'approved', 'rejected'];
const REVIEW_LEVELS: ReviewLevel[] = ['reviewer', 'senior_reviewer'];

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function ReviewerQueuePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page')) ?? DEFAULT_PAGE;
  const size = parsePositiveInt(searchParams.get('size')) ?? DEFAULT_SIZE;
  const verdict = parseVerdict(searchParams.get('verdict'));
  const currentUser = getUser();
  const canSeniorReview = currentUser?.roles.includes('SENIOR_REVIEWER') ?? false;
  const reviewLevel = canSeniorReview ? parseReviewLevel(searchParams.get('reviewLevel')) ?? 'reviewer' : 'reviewer';
  const queueQuery = useReviewerQueueQuery({ page, size, verdict, reviewLevel });
  const batchReviewMutation = useBatchReviewMutation();
  const items = queueQuery.data?.items ?? [];
  const [selectedSubmissionIds, setSelectedSubmissionIds] = useState<number[]>([]);
  const [rejectModalVisible, setRejectModalVisible] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  const columns = useMemo(
    () => [
      {
        title: 'Submission',
        render: (_: unknown, record: ReviewerSubmissionSummary) => (
          <div className="reviewer-submission-cell">
            <Typography.Text strong>#{record.id}</Typography.Text>
            <Typography.Text type="tertiary">Task #{record.taskId}</Typography.Text>
          </div>
        ),
      },
      {
        title: '任务',
        render: (_: unknown, record: ReviewerSubmissionSummary) => (
          <div className="task-title-cell">
            <Typography.Text strong>{record.taskTitle}</Typography.Text>
            <Typography.Text type="tertiary">Labeler #{record.labelerId}</Typography.Text>
          </div>
        ),
      },
      { title: 'Schema', width: 110, render: (_: unknown, record: ReviewerSubmissionSummary) => `#${record.schemaVersionId}` },
      { title: '提交时间', width: 150, render: (_: unknown, record: ReviewerSubmissionSummary) => formatDateTime(record.submittedAt) },
      {
        title: 'Verdict',
        width: 120,
        render: (_: unknown, record: ReviewerSubmissionSummary) => <VerdictTag status={record.verdict.status} />,
      },
      {
        title: '层级',
        width: 90,
        render: (_: unknown, record: ReviewerSubmissionSummary) => <ReviewLevelTag reviewLevel={record.reviewLevel} />,
      },
      {
        title: '操作',
        width: 130,
        render: (_: unknown, record: ReviewerSubmissionSummary) => (
          <Button size="small" icon={<IconPlay />} onClick={() => navigate(`/reviewer/submissions/${record.id}?reviewLevel=${record.reviewLevel}`)}>
            开始审核
          </Button>
        ),
      },
    ],
    [navigate],
  );

  function updateParams(next: { page?: number; size?: number; verdict?: VerdictStatus | null; reviewLevel?: ReviewLevel }) {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(next.page ?? page));
    params.set('size', String(next.size ?? size));
    if (next.verdict === null) params.delete('verdict');
    if (next.verdict) params.set('verdict', next.verdict);
    if (next.reviewLevel) params.set('reviewLevel', next.reviewLevel);
    setSearchParams(params);
  }

  const runBatchReview = async (verdictValue: 'approve' | 'reject', reason?: string) => {
    if (selectedSubmissionIds.length === 0) {
      Toast.warning('请选择要审核的 submission');
      return;
    }
    if (verdictValue === 'reject' && !reason?.trim()) {
      Toast.warning('批量打回必须填写理由');
      return;
    }
    try {
      const result = await batchReviewMutation.mutateAsync({
        submissionIds: selectedSubmissionIds,
        verdict: verdictValue,
        reviewLevel,
        reason: reason?.trim() || undefined,
      });
      const created = result.items.filter((item) => item.status === 'created').length;
      Toast.success(`批量审核完成: ${created}/${result.items.length} 条成功`);
      setSelectedSubmissionIds([]);
      setRejectReason('');
      setRejectModalVisible(false);
      await queueQuery.refetch();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '批量审核失败');
    }
  };

  return (
    <section className="reviewer-queue-page reviewer-queue-page--workbench" aria-label="Reviewer queue">
      <header className="reviewer-workbench-hero">
        <div className="reviewer-workbench-hero__copy">
          <div className="reviewer-role-stack" aria-label="当前审核角色">
            <RoleBadge role="REVIEWER" />
            {canSeniorReview ? <RoleBadge role="SENIOR_REVIEWER" /> : null}
          </div>
          <Typography.Title heading={3} className="page-title">
            审核队列
          </Typography.Title>
          <div className="reviewer-ledger-subtitle">
            <Typography.Text>从 append-only Quality Ledger 派生当前 Verdict。</Typography.Text>
            <Tooltip content="Verdict 由最新 ledger entry 派生,不会直接依赖 submission.status。">
              <IconInfoCircle aria-label="Quality Ledger verdict derivation" />
            </Tooltip>
          </div>
        </div>
        <div className="reviewer-filter-strip">
          {canSeniorReview ? (
            <Select
              className="reviewer-filter-select"
              size="small"
              value={reviewLevel}
              onChange={(value) => updateParams({ page: 1, reviewLevel: value as ReviewLevel })}
            >
              {REVIEW_LEVELS.map((item) => (
                <Select.Option key={item} value={item}>
                  {REVIEW_LEVEL_LABELS[item]}
                </Select.Option>
              ))}
            </Select>
          ) : null}
          <Select
            className="reviewer-filter-select"
            size="small"
            value={verdict ?? 'all'}
            onChange={(value) => updateParams({ page: 1, verdict: value === 'all' ? null : (value as VerdictStatus) })}
          >
            <Select.Option value="all">全部 Verdict</Select.Option>
            {VERDICT_FILTERS.map((item) => (
              <Select.Option key={item} value={item}>
              {VERDICT_STATUS_LABELS[item]}
            </Select.Option>
          ))}
          </Select>
        </div>
      </header>

      <div className="reviewer-queue-flow-strip" aria-label="审核流转">
        <span className="review-flow-node review-flow-node--submitted">提交</span>
        <span className="review-flow-connector" />
        <span className="review-flow-node review-flow-node--active">初审</span>
        <span className="review-flow-connector" />
        <span className="review-flow-node review-flow-node--senior">高级审核</span>
        <span className="review-flow-connector" />
        <span className="review-flow-node review-flow-node--terminal">通过 / 打回</span>
      </div>

      <div className="task-toolbar reviewer-queue-toolbar">
        <Typography.Text type="tertiary">共 {queueQuery.data?.total ?? 0} 条 submitted submission</Typography.Text>
        <Space>
          <Button
            size="small"
            disabled={selectedSubmissionIds.length === 0}
            loading={batchReviewMutation.isPending}
            onClick={() => void runBatchReview('approve')}
          >
            批量通过
          </Button>
          <Button
            size="small"
            disabled={selectedSubmissionIds.length === 0}
            loading={batchReviewMutation.isPending}
            onClick={() => setRejectModalVisible(true)}
          >
            批量打回
          </Button>
          <Button icon={<IconRefresh />} size="small" onClick={() => queueQuery.refetch()} loading={queueQuery.isFetching}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="task-table-surface task-table-surface--reviewer">
        {queueQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}
        {queueQuery.isError ? (
          <div className="task-state-panel">
            <Empty title="审核队列加载失败" description={queueQuery.error instanceof Error ? queueQuery.error.message : '请稍后重试。'} />
          </div>
        ) : null}
        {!queueQuery.isLoading && !queueQuery.isError && items.length === 0 ? (
          <div className="task-state-panel">
            <Empty title="暂无可审核 submission" description="当前筛选条件下没有 submitted submission。" />
          </div>
        ) : null}
        {items.length > 0 ? (
          <>
            <Table
              columns={columns}
              dataSource={items}
              rowKey="id"
              pagination={false}
              rowSelection={{
                selectedRowKeys: selectedSubmissionIds,
                onChange: (keys) => setSelectedSubmissionIds((keys ?? []).map((key) => Number(key))),
              }}
            />
            <div className="task-pagination">
              <Pagination
                total={queueQuery.data?.total ?? 0}
                currentPage={page}
                pageSize={size}
                showSizeChanger
                onPageChange={(nextPage) => updateParams({ page: nextPage })}
                onPageSizeChange={(nextSize) => updateParams({ page: 1, size: nextSize })}
              />
            </div>
          </>
        ) : null}
      </div>
      <Modal
        title="批量打回"
        visible={rejectModalVisible}
        okText="确认打回"
        cancelText="取消"
        confirmLoading={batchReviewMutation.isPending}
        onCancel={() => setRejectModalVisible(false)}
        onOk={() => void runBatchReview('reject', rejectReason)}
      >
        <TextArea
          value={rejectReason}
          placeholder="填写本次批量打回理由"
          autosize
          onChange={setRejectReason}
        />
      </Modal>
    </section>
  );
}

function VerdictTag({ status }: { status: VerdictStatus }) {
  return <Tag className={`semantic-tag semantic-tag--${verdictTone(status)}`}>{VERDICT_STATUS_LABELS[status]}</Tag>;
}

function verdictTone(status: VerdictStatus) {
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

function parsePositiveInt(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function parseVerdict(value: string | null): VerdictStatus | undefined {
  return VERDICT_FILTERS.includes(value as VerdictStatus) ? (value as VerdictStatus) : undefined;
}

function parseReviewLevel(value: string | null): ReviewLevel | undefined {
  return REVIEW_LEVELS.includes(value as ReviewLevel) ? (value as ReviewLevel) : undefined;
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
