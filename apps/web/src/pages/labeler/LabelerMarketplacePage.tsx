import { Button, Empty, Input, InputNumber, Select, Space, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconChevronLeft, IconChevronRight, IconPlay, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { MarketplaceTask } from '../../entities/submission/submissionTypes';
import { ClaimTaskFailure, useClaimBatchMutation } from '../../features/labeling/useClaimMutation';
import { useMarketplaceQuery } from '../../features/labeling/useMarketplaceQuery';
import { RoleBadge } from '../../shared/ui/RoleBadge';
import { LabelerTaskDetailDrawer } from './LabelerTaskDetailDrawer';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;
const DEFAULT_CLAIM_SIZE_CAP = 10;

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

function claimLimitForTask(task: MarketplaceTask) {
  return Math.max(0, task.availableItemCount);
}

function normalizeClaimSize(value: unknown, max: number) {
  const upperBound = Math.max(1, Math.trunc(max));
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 1;
  }
  return Math.min(Math.max(1, Math.trunc(numeric)), upperBound);
}

function defaultClaimSizeForTask(task: MarketplaceTask) {
  const limit = claimLimitForTask(task);
  return limit > 0 ? Math.min(DEFAULT_CLAIM_SIZE_CAP, limit) : 1;
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
  const claimMutation = useClaimBatchMutation();
  const [claimingTaskId, setClaimingTaskId] = useState<number | null>(null);
  const [claimSizeByTaskId, setClaimSizeByTaskId] = useState<Record<number, number>>({});
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

  const claimSizeForTask = (task: MarketplaceTask) =>
    normalizeClaimSize(claimSizeByTaskId[task.id] ?? defaultClaimSizeForTask(task), claimLimitForTask(task));

  const setClaimSizeForTask = (task: MarketplaceTask, value: unknown) => {
    setClaimSizeByTaskId((current) => ({
      ...current,
      [task.id]: normalizeClaimSize(value, claimLimitForTask(task)),
    }));
  };

  const taskById = (taskId: number) => items.find((item) => item.id === taskId) ?? selectedTask;

  const handleClaim = async (taskId: number, requestedSize?: number) => {
    const task = taskById(taskId);
    const size = task ? normalizeClaimSize(requestedSize ?? claimSizeForTask(task), claimLimitForTask(task)) : 1;
    setClaimingTaskId(taskId);
    try {
      const result = await claimMutation.mutateAsync({ size, taskId });
      const firstSession = result.sessions[0];
      if (!firstSession) {
        Toast.warning('暂无可领取的数据项');
        return;
      }
      Toast.success(result.claimedCount < result.requestedSize ? `已领取 ${result.claimedCount} 条,已按可用题目截断` : `已领取 ${result.claimedCount} 条,开始作答`);
      setSelectedTask(null);
      navigate(`/labeler/sessions/${firstSession.id}`);
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
          className="marketplace-deadline-filter"
          value={deadline ?? 'all'}
          onChange={(value) => setFilterParam('deadline', value === 'all' ? null : String(value))}
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
                  <span>已领取 {record.quotaClaimed} / 题量 {record.quotaTotal}</span>
                  <span>截止 {formatDateTime(record.deadlineAt)}</span>
                </div>
                <div className="marketplace-task-card__actions">
                  <Button onClick={() => setSelectedTask(record)}>查看详情</Button>
                  <div className="marketplace-claim-actions">
                    <label className="marketplace-claim-control">
                      <span>领取数量</span>
                      <InputNumber
                        aria-label={`领取${record.title}数量`}
                        disabled={claimLimitForTask(record) <= 0}
                        max={Math.max(1, claimLimitForTask(record))}
                        min={1}
                        precision={0}
                        value={claimSizeForTask(record)}
                        onChange={(value) => setClaimSizeForTask(record, value)}
                      />
                    </label>
                    <Button
                      className="labeler-task-card__cta"
                      icon={<IconPlay />}
                      theme="solid"
                      type="primary"
                      loading={claimingTaskId === record.id && claimMutation.isPending}
                      onClick={() => handleClaim(record.id, claimSizeForTask(record))}
                    >
                      领取 {claimSizeForTask(record)} 条
                    </Button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </div>
      <LabelerTaskDetailDrawer
        claimLimit={selectedTask ? claimLimitForTask(selectedTask) : 0}
        claimSize={selectedTask ? claimSizeForTask(selectedTask) : 1}
        claiming={selectedTask ? claimingTaskId === selectedTask.id && claimMutation.isPending : false}
        onClaim={handleClaim}
        onClaimSizeChange={(value) => {
          if (selectedTask) {
            setClaimSizeForTask(selectedTask, value);
          }
        }}
        onClose={() => setSelectedTask(null)}
        task={selectedTask}
      />
    </section>
  );
}
