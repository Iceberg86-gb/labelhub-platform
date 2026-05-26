import { Button, Empty, Space, Spin, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconChevronLeft, IconChevronRight, IconPlay, IconRefresh } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { MarketplaceTask } from '../../entities/submission/submissionTypes';
import { ClaimTaskFailure, useClaimMutation } from '../../features/labeling/useClaimMutation';
import { useMarketplaceQuery } from '../../features/labeling/useMarketplaceQuery';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(
        new Date(value),
      )
    : '-';
}

export function LabelerMarketplacePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const marketplaceQuery = useMarketplaceQuery({ page, size });
  const claimMutation = useClaimMutation();
  const [claimingTaskId, setClaimingTaskId] = useState<number | null>(null);

  const updatePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage));
    next.set('size', String(size));
    setSearchParams(next);
  };

  const handleClaim = async (taskId: number) => {
    setClaimingTaskId(taskId);
    try {
      const session = await claimMutation.mutateAsync(taskId);
      Toast.success('已领取,开始作答');
      navigate(`/labeler/sessions/${session.id}`);
    } catch (error) {
      if (error instanceof ClaimTaskFailure) {
        Toast.warning(error.message);
      } else {
        Toast.error('领取任务失败,请稍后重试');
      }
    } finally {
      setClaimingTaskId(null);
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '任务',
        dataIndex: 'title',
        render: (_: unknown, record: MarketplaceTask) => (
          <div className="schema-title-cell">
            <Typography.Text strong>{record.title}</Typography.Text>
            <Typography.Text type="tertiary">{record.description || '暂无描述'}</Typography.Text>
          </div>
        ),
      },
      {
        title: '可用数据',
        width: 110,
        render: (_: unknown, record: MarketplaceTask) => <Tag color="green">{record.availableItemCount}</Tag>,
      },
      {
        title: '配额',
        width: 140,
        render: (_: unknown, record: MarketplaceTask) => `${record.quotaClaimed}/${record.quotaTotal}`,
      },
      {
        title: '截止时间',
        width: 150,
        render: (_: unknown, record: MarketplaceTask) => formatDateTime(record.deadlineAt),
      },
      {
        title: '操作',
        width: 130,
        render: (_: unknown, record: MarketplaceTask) => (
          <Button
            icon={<IconPlay />}
            loading={claimingTaskId === record.id && claimMutation.isPending}
            onClick={() => handleClaim(record.id)}
          >
            领取
          </Button>
        ),
      },
    ],
    [claimMutation.isPending, claimingTaskId],
  );

  const data = marketplaceQuery.data;
  const items = data?.items ?? [];
  const hasNext = page * size < (data?.total ?? 0);

  return (
    <section className="labeler-page" aria-label="Labeler marketplace">
      <div className="page-heading">
        <div>
          <Typography.Title heading={3} className="page-title">
            任务广场
          </Typography.Title>
          <Typography.Text type="tertiary">领取已发布且仍有可用数据项的任务。</Typography.Text>
        </div>
        <Button icon={<IconRefresh />} onClick={() => marketplaceQuery.refetch()} loading={marketplaceQuery.isFetching}>
          刷新
        </Button>
      </div>

      <div className="task-toolbar">
        <Typography.Text type="tertiary">共 {data?.total ?? 0} 个可领取任务</Typography.Text>
        <Space>
          <Button icon={<IconChevronLeft />} disabled={page <= 1} onClick={() => updatePage(page - 1)}>
            上一页
          </Button>
          <Typography.Text>第 {page} 页</Typography.Text>
          <Button icon={<IconChevronRight />} disabled={!hasNext} onClick={() => updatePage(page + 1)}>
            下一页
          </Button>
        </Space>
      </div>

      <div className="task-table-surface">
        {marketplaceQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}
        {marketplaceQuery.isError ? (
          <div className="task-state-panel">
            <Empty title="任务广场加载失败" description="请稍后重试。" />
            <Button onClick={() => marketplaceQuery.refetch()}>重新加载</Button>
          </div>
        ) : null}
        {!marketplaceQuery.isLoading && !marketplaceQuery.isError && items.length === 0 ? (
          <div className="task-state-panel">
            <Empty title="暂无可领取任务" description="稍后再回来看看。" />
          </div>
        ) : null}
        {items.length > 0 ? <Table columns={columns} dataSource={items} rowKey="id" pagination={false} /> : null}
      </div>
    </section>
  );
}
