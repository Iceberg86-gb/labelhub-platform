import { Button, Empty, Pagination, Popconfirm, Select, Space, Spin, Table, Toast, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconExternalOpen, IconPlus, IconRefresh } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { TaskStatusBadge } from '../../entities/task/TaskStatusBadge';
import { CreateTaskModal } from '../../features/task/create-task/CreateTaskModal';
import { useDeleteTaskMutation } from '../../features/task/delete-task/useDeleteTaskMutation';
import { useTasksQuery, type Task, type TaskStatus } from '../../features/task/list-tasks/useTasksQuery';
import { IconDataset, IconDesignerBlock, IconStatusFlow, IconTask } from '../../shared/ui/LabelHubIcons';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;
const TASK_STATUSES: TaskStatus[] = ['draft', 'published', 'paused', 'ended'];

const statusOptions: Array<{ label: string; value: TaskStatus | 'all' }> = [
  { label: '全部状态', value: 'all' },
  { label: '草稿', value: 'draft' },
  { label: '发布中', value: 'published' },
  { label: '已暂停', value: 'paused' },
  { label: '已结束', value: 'ended' },
];

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseStatus(value: string | null): TaskStatus | undefined {
  return TASK_STATUSES.includes(value as TaskStatus) ? (value as TaskStatus) : undefined;
}

function formatDateTime(value?: string, fallback = '-') {
  if (!value) {
    return fallback;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export function OwnerTasksListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [createVisible, setCreateVisible] = useState(false);

  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const status = parseStatus(searchParams.get('status'));
  const tasksQuery = useTasksQuery({ page, size, status });
  const deleteTaskMutation = useDeleteTaskMutation();
  const deletingTaskId = deleteTaskMutation.isPending ? deleteTaskMutation.variables?.taskId : undefined;

  const updateParams = (patch: { page?: number; size?: number; status?: TaskStatus }) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(patch.page ?? page));
    next.set('size', String(patch.size ?? size));

    if (patch.status) {
      next.set('status', patch.status);
    } else if ('status' in patch) {
      next.delete('status');
    }

    setSearchParams(next);
  };

  const handleDeleteTask = (task: Task) =>
    deleteTaskMutation
      .mutateAsync({ taskId: task.id })
      .then(() => Toast.success('任务已永久删除'))
      .catch((error: unknown) => Toast.error(error instanceof Error ? error.message : '删除失败,请稍后重试'));

  const columns = useMemo(
    () => [
      {
        title: '任务标题',
        dataIndex: 'title',
        render: (_: unknown, record: Task) => (
          <div className="task-title-cell">
            <Typography.Text strong>{record.title}</Typography.Text>
            {record.tags?.length ? <Typography.Text type="tertiary">{record.tags.join(' / ')}</Typography.Text> : null}
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (_: unknown, record: Task) => <TaskStatusBadge status={record.status} />,
      },
      {
        title: '领取/题量',
        dataIndex: 'quotaTotal',
        width: 130,
        render: (_: unknown, record: Task) => `${record.quotaClaimed}/${record.quotaTotal}`,
      },
      {
        title: '截止时间',
        dataIndex: 'deadlineAt',
        width: 150,
        render: (value?: string) => formatDateTime(value),
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        width: 150,
        render: (value?: string) => formatDateTime(value, '未记录'),
      },
      {
        title: '操作',
        width: 200,
        render: (_: unknown, record: Task) => (
          <Space>
            <Button icon={<IconExternalOpen />} size="small" theme="borderless" onClick={() => navigate(`/owner/tasks/${record.id}`)}>
              查看详情
            </Button>
            <Popconfirm
              title="永久删除任务?"
              content={
                <Typography.Text style={{ display: 'block', maxWidth: 360, lineHeight: 1.6 }}>
                  此操作会永久删除该 task 以及所有 task 范围内的事实数据(sessions、submissions、AI 调用、Quality
                  Ledger、Verdict、Export 快照等)。删除后不可恢复。
                </Typography.Text>
              }
              position="leftTop"
              okText="永久删除"
              cancelText="取消"
              okType="danger"
              onConfirm={() => handleDeleteTask(record)}
            >
              <Button icon={<IconDelete />} size="small" theme="borderless" type="danger" loading={deletingTaskId === record.id}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [deletingTaskId, deleteTaskMutation, navigate],
  );

  const data = tasksQuery.data;
  const items = data?.items ?? [];
  const isEmpty = !tasksQuery.isLoading && !tasksQuery.isError && items.length === 0;
  const pageStatusCounts = items.reduce<Record<TaskStatus, number>>(
    (counts, task) => {
      counts[task.status] += 1;
      return counts;
    },
    { draft: 0, ended: 0, paused: 0, published: 0 },
  );
  const claimedItemCount = items.reduce((sum, task) => sum + task.quotaClaimed, 0);
  const totalItemCount = items.reduce((sum, task) => sum + task.quotaTotal, 0);

  return (
    <section className="tasks-page owner-task-page" aria-label="Owner task list">
      <header className="owner-page-hero">
        <div className="owner-page-hero__copy">
          <div className="owner-page-hero__meta">
            <RoleBadge role="OWNER" />
            <Typography.Text>任务配置与发布</Typography.Text>
          </div>
          <Typography.Title heading={3} className="page-title">
            任务管理
          </Typography.Title>
          <Typography.Text type="tertiary">
            管理标注任务的生命周期、题量发布与后续 Designer / 数据集配置入口。
          </Typography.Text>
        </div>
        <Button icon={<IconPlus />} theme="solid" type="primary" onClick={() => setCreateVisible(true)}>
          创建任务
        </Button>
      </header>

      <section className="owner-task-overview" aria-label="任务概览">
        <div className="owner-task-stat owner-task-stat--primary">
          <span className="owner-task-stat__icon"><IconTask /></span>
          <span>任务总数</span>
          <strong>{data?.total ?? 0}</strong>
        </div>
        <div className="owner-task-stat">
          <span className="owner-task-stat__icon"><IconStatusFlow /></span>
          <span>本页发布中</span>
          <strong>{pageStatusCounts.published}</strong>
        </div>
        <div className="owner-task-stat">
          <span className="owner-task-stat__icon"><IconDesignerBlock /></span>
          <span>本页草稿</span>
          <strong>{pageStatusCounts.draft}</strong>
        </div>
        <div className="owner-task-stat">
          <span className="owner-task-stat__icon"><IconDataset /></span>
          <span>本页领取</span>
          <strong>{claimedItemCount}/{totalItemCount}</strong>
        </div>
      </section>

      <div className="task-toolbar owner-task-toolbar">
        <Space>
          <Select
            aria-label="任务状态筛选"
            value={status ?? 'all'}
            style={{ width: 168 }}
            optionList={statusOptions}
            onChange={(value) => {
              updateParams({ page: 1, status: value === 'all' ? undefined : (value as TaskStatus) });
            }}
          />
          <Button icon={<IconRefresh />} onClick={() => tasksQuery.refetch()} loading={tasksQuery.isFetching}>
            刷新
          </Button>
        </Space>
        <Typography.Text type="tertiary">共 {data?.total ?? 0} 条</Typography.Text>
      </div>

      <div className="task-table-surface task-table-surface--owner">
        {tasksQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}

        {tasksQuery.isError ? (
          <div className="task-state-panel">
            <Empty
              title="任务列表加载失败"
              description={tasksQuery.error instanceof Error ? tasksQuery.error.message : '请稍后重试。'}
            />
            <Button onClick={() => tasksQuery.refetch()}>重新加载</Button>
          </div>
        ) : null}

        {isEmpty ? (
          <div className="task-state-panel">
            <Empty title="暂无任务" description="创建第一个任务，开始 Owner 管理链路演示。" />
            <Button icon={<IconPlus />} theme="solid" type="primary" onClick={() => setCreateVisible(true)}>
              创建第一个任务
            </Button>
          </div>
        ) : null}

        {!tasksQuery.isLoading && !tasksQuery.isError && items.length > 0 ? (
          <>
            <Table columns={columns} dataSource={items} rowKey="id" pagination={false} />
            <div className="task-pagination">
              <Pagination
                total={data?.total ?? 0}
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

      <CreateTaskModal visible={createVisible} onClose={() => setCreateVisible(false)} />
    </section>
  );
}
