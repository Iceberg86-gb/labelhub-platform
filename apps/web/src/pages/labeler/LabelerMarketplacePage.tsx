import { Button, Empty, Input, Select, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconChevronLeft, IconChevronRight, IconPlay, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { MarketplaceTask } from '../../entities/submission/submissionTypes';
import { ClaimTaskFailure, useClaimMutation } from '../../features/labeling/useClaimMutation';
import { useMarketplaceQuery } from '../../features/labeling/useMarketplaceQuery';
import { RoleBadge } from '../../shared/ui/RoleBadge';
import { LabelerTaskDetailDrawer } from './LabelerTaskDetailDrawer';

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

function optionalParam(value: string) {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : undefined;
}

function marketplaceDeadline(value: string | null): 'day' | 'week' | undefined {
  return value === 'day' || value === 'week' ? value : undefined;
}

export function LabelerMarketplacePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const q = searchParams.get('q') ?? '';
  const tag = searchParams.get('tag') ?? '';
  const hasReward = searchParams.get('hasReward') === 'true';
  const deadline = marketplaceDeadline(searchParams.get('deadline'));
  const marketplaceQuery = useMarketplaceQuery({
    page,
    size,
    q: optionalParam(q),
    tag: optionalParam(tag),
    hasReward: hasReward || undefined,
    deadline,
  });
  const claimMutation = useClaimMutation();
  const [claimingTaskId, setClaimingTaskId] = useState<number | null>(null);
  const [draftSearch, setDraftSearch] = useState({ q, tag });
  const [selectedTask, setSelectedTask] = useState<MarketplaceTask | null>(null);

  useEffect(() => {
    setDraftSearch({ q, tag });
  }, [q, tag]);

  const updatePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage));
    next.set('size', String(size));
    setSearchParams(next);
  };

  const setFilterParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', '1');
    next.set('size', String(size));
    if (value && value.trim().length > 0) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setSearchParams(next);
  };

  const applySearchFilters = () => {
    const next = new URLSearchParams(searchParams);
    next.set('page', '1');
    next.set('size', String(size));
    const normalizedQ = optionalParam(draftSearch.q);
    const normalizedTag = optionalParam(draftSearch.tag);
    if (normalizedQ) {
      next.set('q', normalizedQ);
    } else {
      next.delete('q');
    }
    if (normalizedTag) {
      next.set('tag', normalizedTag);
    } else {
      next.delete('tag');
    }
    setSearchParams(next);
  };

  const resetFilters = () => {
    const next = new URLSearchParams();
    next.set('page', '1');
    next.set('size', String(size));
    setSearchParams(next);
  };

  const handleClaim = async (taskId: number) => {
    setClaimingTaskId(taskId);
    try {
      const session = await claimMutation.mutateAsync(taskId);
      Toast.success('已领取,开始作答');
      setSelectedTask(null);
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

  const data = marketplaceQuery.data;
  const items = data?.items ?? [];
  const hasNext = page * size < (data?.total ?? 0);
  const hasActiveFilters = Boolean(q || tag || hasReward || deadline);

  return (
    <section className="labeler-page labeler-page--marketplace" aria-label="Labeler marketplace">
      <header className="labeler-workbench-hero">
        <div className="labeler-workbench-hero__copy">
          <RoleBadge role="LABELER" />
          <Typography.Title heading={3} className="page-title">
            任务广场
          </Typography.Title>
          <Typography.Text type="tertiary">
            领取已发布且仍有可用数据项的任务。每次领取会分配一个可用数据项;同一任务可多次领取不同 item。
          </Typography.Text>
        </div>
        <div className="labeler-workbench-hero__actions">
          <Button icon={<IconRefresh />} onClick={() => marketplaceQuery.refetch()} loading={marketplaceQuery.isFetching}>
            刷新
          </Button>
        </div>
      </header>

      <div className="marketplace-filter-bar labeler-workbench-filter-bar" aria-label="任务广场筛选">
        <Input
          prefix={<IconSearch />}
          value={draftSearch.q}
          placeholder="搜索标题、描述或标签"
          onChange={(value) => setDraftSearch((current) => ({ ...current, q: value }))}
          onEnterPress={applySearchFilters}
        />
        <Input
          value={draftSearch.tag}
          placeholder="标签"
          onChange={(value) => setDraftSearch((current) => ({ ...current, tag: value }))}
          onEnterPress={applySearchFilters}
        />
        <Select
          value={deadline ?? 'all'}
          onChange={(value) => setFilterParam('deadline', value === 'all' ? null : String(value))}
          style={{ width: 132 }}
          aria-label="截止时间筛选"
        >
          <Select.Option value="all">全部截止</Select.Option>
          <Select.Option value="day">24 小时内</Select.Option>
          <Select.Option value="week">7 天内</Select.Option>
        </Select>
        <Button type={hasReward ? 'primary' : 'tertiary'} onClick={() => setFilterParam('hasReward', hasReward ? null : 'true')}>
          有奖励
        </Button>
        <Button icon={<IconSearch />} theme="solid" type="primary" onClick={applySearchFilters}>
          查询
        </Button>
        <Button disabled={!hasActiveFilters} onClick={resetFilters}>
          重置
        </Button>
      </div>

      <div className="task-toolbar labeler-workbench-toolbar">
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

      <div className="task-table-surface task-table-surface--labeler">
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
        {items.length > 0 ? (
          <div className="marketplace-card-grid">
            {items.map((record: MarketplaceTask) => (
              <article className="marketplace-task-card marketplace-task-card--claimable" key={record.id}>
                <div className="marketplace-task-card__main">
                  <div className="marketplace-task-card__heading">
                    <Typography.Title heading={5}>{record.title}</Typography.Title>
                    <Tag className="semantic-tag semantic-tag--success">{record.availableItemCount} 可领取</Tag>
                  </div>
                  <Typography.Paragraph ellipsis={{ rows: 2 }} type="tertiary">
                    {record.description || '暂无描述'}
                  </Typography.Paragraph>
                  {record.tags && record.tags.length > 0 ? (
                    <Space wrap spacing={4}>
                      {record.tags.slice(0, 4).map((item) => (
                        <Tag key={item} className="semantic-tag semantic-tag--accent">
                          {item}
                        </Tag>
                      ))}
                    </Space>
                  ) : null}
                </div>
                <div className="marketplace-task-card__meta">
                  <span>配额 {record.quotaClaimed}/{record.quotaTotal}</span>
                  <span>截止 {formatDateTime(record.deadlineAt)}</span>
                </div>
                <div className="marketplace-task-card__actions">
                  <Button onClick={() => setSelectedTask(record)}>查看详情</Button>
                  <Button
                    className="labeler-task-card__cta"
                    icon={<IconPlay />}
                    theme="solid"
                    type="primary"
                    loading={claimingTaskId === record.id && claimMutation.isPending}
                    onClick={() => handleClaim(record.id)}
                  >
                    领取
                  </Button>
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </div>
      <LabelerTaskDetailDrawer
        claiming={selectedTask ? claimingTaskId === selectedTask.id && claimMutation.isPending : false}
        onClaim={handleClaim}
        onClose={() => setSelectedTask(null)}
        task={selectedTask}
      />
    </section>
  );
}
