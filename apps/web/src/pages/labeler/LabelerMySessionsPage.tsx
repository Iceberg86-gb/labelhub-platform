import { Button, Empty, Select, Space, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import { IconChevronLeft, IconChevronRight, IconPlay } from '@douyinfe/semi-icons';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  SESSION_STATUS_LABELS,
  SESSION_STATUSES,
  type Session,
  type SessionStatus,
} from '../../entities/submission/submissionTypes';
import { useMySessionsQuery } from '../../features/labeling/useMySessionsQuery';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseStatus(value: string | null): SessionStatus | undefined {
  return SESSION_STATUSES.includes(value as SessionStatus) ? (value as SessionStatus) : undefined;
}

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(
        new Date(value),
      )
    : '—';
}

function statusColor(status: SessionStatus) {
  if (status === 'claimed') return 'blue';
  if (status === 'submitted') return 'green';
  if (status === 'returned_for_revision') return 'amber';
  return 'grey';
}

export function LabelerMySessionsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const status = parseStatus(searchParams.get('status'));
  const sessionsQuery = useMySessionsQuery({ page, size, status });
  const data = sessionsQuery.data;
  const items = data?.items ?? [];
  const hasNext = page * size < (data?.total ?? 0);

  const updateParams = (next: { page?: number; status?: SessionStatus | null }) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(next.page ?? page));
    params.set('size', String(size));
    if (next.status === null) params.delete('status');
    if (next.status) params.set('status', next.status);
    setSearchParams(params);
  };

  const columns = useMemo(
    () => [
      {
        title: '任务',
        render: (_: unknown, record: Session) => (
          <div className="schema-title-cell">
            <Typography.Text strong>Task #{record.taskId}</Typography.Text>
            <Typography.Text type="tertiary">Session #{record.id}</Typography.Text>
          </div>
        ),
      },
      {
        title: 'Schema',
        width: 130,
        render: (_: unknown, record: Session) => <Tag color="blue">Schema #{record.schemaVersionId}</Tag>,
      },
      {
        title: '状态',
        width: 110,
        render: (_: unknown, record: Session) => (
          <Tag color={statusColor(record.status)}>{SESSION_STATUS_LABELS[record.status]}</Tag>
        ),
      },
      {
        title: '领取时间',
        width: 150,
        render: (_: unknown, record: Session) => formatDateTime(record.claimedAt),
      },
      {
        title: '提交时间',
        width: 150,
        render: (_: unknown, record: Session) => formatDateTime(record.submittedAt),
      },
      {
        title: '操作',
        width: 120,
        render: (_: unknown, record: Session) => (
          <Button icon={<IconPlay />} onClick={() => navigate(`/labeler/sessions/${record.id}`)}>
            {record.status === 'claimed' || record.status === 'returned_for_revision' ? '继续作答' : '查看作答'}
          </Button>
        ),
      },
    ],
    [navigate],
  );

  return (
    <section className="labeler-page" aria-label="Labeler sessions">
      <div className="page-heading">
        <div>
          <Typography.Title heading={3} className="page-title">
            我的数据
          </Typography.Title>
          <Typography.Text type="tertiary">查看已领取和已提交的作答会话。</Typography.Text>
        </div>
        <Select
          className="session-status-filter"
          value={status ?? 'all'}
          onChange={(value) => updateParams({ page: 1, status: value === 'all' ? null : (value as SessionStatus) })}
        >
          <Select.Option value="all">全部状态</Select.Option>
          {SESSION_STATUSES.map((item) => (
            <Select.Option key={item} value={item}>
              {SESSION_STATUS_LABELS[item]}
            </Select.Option>
          ))}
        </Select>
      </div>

      <div className="task-toolbar">
        <Typography.Text type="tertiary">共 {data?.total ?? 0} 个会话</Typography.Text>
        <Space>
          <Button icon={<IconChevronLeft />} disabled={page <= 1} onClick={() => updateParams({ page: page - 1 })}>
            上一页
          </Button>
          <Typography.Text>第 {page} 页</Typography.Text>
          <Button icon={<IconChevronRight />} disabled={!hasNext} onClick={() => updateParams({ page: page + 1 })}>
            下一页
          </Button>
        </Space>
      </div>

      <div className="task-table-surface">
        {sessionsQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}
        {sessionsQuery.isError ? (
          <div className="task-state-panel">
            <Empty title="我的数据加载失败" description="请稍后重试。" />
          </div>
        ) : null}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && items.length === 0 ? (
          <div className="task-state-panel">
            <Empty title="暂无作答会话" description="去任务广场领取一个任务开始作答。" />
          </div>
        ) : null}
        {items.length > 0 ? <Table columns={columns} dataSource={items} rowKey="id" pagination={false} /> : null}
      </div>
    </section>
  );
}
