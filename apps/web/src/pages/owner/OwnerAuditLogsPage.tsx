import { Button, DatePicker, Empty, Input, Modal, Pagination, Select, Space, Spin, Table, Tag, Typography, Tooltip } from '@douyinfe/semi-ui';
import { IconDownload, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuditLogExportMutation } from '../../features/audit/useAuditLogExportMutation';
import { AuditLogsQueryError, useAuditLogsQuery, type AuditLog, type AuditLogsFilters } from '../../features/audit/useAuditLogsQuery';

const PAGE_SIZE = 10;
const ACTIONS = [
  ['task.transition', '任务 / 状态迁移'],
  ['task.delete', '任务 / 硬删除'],
  ['schema.publish', 'Schema / 发布'],
  ['schema.version_create', 'Schema / 版本创建'],
  ['submission.create', '提交 / 创建'],
  ['ai_review.field_assist', 'AI / 字段协助'],
  ['ai_review.failed', 'AI / 审核失败'],
  ['ai_review.recorded_failed_call', 'AI / 失败调用记录'],
  ['review.approve', '审核 / 通过'],
  ['review.reject', '审核 / 拒绝'],
  ['export.snapshot_create', '导出 / 快照创建'],
  ['export.snapshot_archive', '导出 / 快照归档'],
] as const;
const RESOURCES = ['task', 'schema', 'schema_version', 'submission', 'ai_call', 'export_snapshot'];
const ACTION_OPTIONS = ACTIONS.map(([value, label]) => ({ value, label }));
const ACTION_LABELS = new Map<string, string>(ACTIONS.map(([value, label]) => [value, label.split(' / ')[1]]));
const RESOURCE_OPTIONS = RESOURCES.map((value) => ({ value, label: value }));

type Draft = { actions: string[]; resources: string[]; actor: string; resource: string; dates: Date[] };

const toArray = (value: unknown) => (Array.isArray(value) ? value.map(String) : []);
const onlyDigits = (value: string) => value.replace(/\D/g, '');
const toInt = (value: string) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
};
const toDates = (value: unknown) =>
  Array.isArray(value)
    ? value.map((item) => (item instanceof Date ? item : new Date(String(item)))).filter((date) => !Number.isNaN(date.getTime()))
    : [];
const fmt = (value?: string) =>
  value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
const payload = (log: AuditLog) => log.payload as Record<string, unknown>;
const preview = (log: AuditLog) => {
  const text = JSON.stringify(log.payload ?? {});
  return text.length > 200 ? `${text.slice(0, 200)}...` : text;
};
const fullPayload = (log: AuditLog) => JSON.stringify(log.payload ?? {}, null, 2);

function linkFor(log: AuditLog) {
  const taskId = Number(payload(log).taskId);
  if (log.resourceType === 'task' && log.resourceId) return `/owner/tasks/${log.resourceId}`;
  if (log.resourceType === 'schema' && log.resourceId) return `/owner/schemas/${log.resourceId}/design`;
  if (log.resourceType === 'submission' && log.resourceId && Number.isInteger(taskId)) return `/owner/tasks/${taskId}/submissions/${log.resourceId}`;
  if (log.resourceType === 'export_snapshot' && Number.isInteger(taskId)) return `/owner/tasks/${taskId}`;
  return undefined;
}

function filtersFromDraft(draft: Draft): Omit<AuditLogsFilters, 'page' | 'size'> {
  return {
    actionTypes: draft.actions,
    resourceTypes: draft.resources,
    actorUserId: toInt(draft.actor),
    resourceId: toInt(draft.resource),
    from: draft.dates[0]?.toISOString(),
    to: draft.dates[1]?.toISOString(),
  };
}

export function OwnerAuditLogsPage() {
  const emptyDraft = { actions: [], resources: [], actor: '', resource: '', dates: [] };
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [active, setActive] = useState<Omit<AuditLogsFilters, 'page' | 'size'>>({});
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const filters = useMemo(() => ({ page, size, ...active }), [active, page, size]);
  const logs = useAuditLogsQuery(filters);
  const exportCsv = useAuditLogExportMutation();
  const items = logs.data?.items ?? [];

  const columns = useMemo(
    () => [
      { title: '时间', dataIndex: 'createdAt', width: 150, render: (value?: string) => fmt(value) },
      {
        title: '操作者',
        width: 150,
        render: (_: unknown, log: AuditLog) => (
          <div className="audit-actor-cell">
            <Typography.Text strong>{log.actorDisplayName ?? (log.actorId ? `${log.actorType}#${log.actorId}` : log.actorType)}</Typography.Text>
            <Typography.Text type="tertiary">{log.actorType}</Typography.Text>
          </div>
        ),
      },
      {
        title: '操作',
        width: 180,
        render: (_: unknown, log: AuditLog) => (
          <span className="audit-action-cell">
            <Tooltip content={<code>{log.action}</code>}>
              <Tag className="semantic-tag semantic-tag--accent">{ACTION_LABELS.get(log.action) ?? log.action}</Tag>
            </Tooltip>
          </span>
        ),
      },
      {
        title: '资源',
        width: 170,
        render: (_: unknown, log: AuditLog) => {
          const text = `${log.resourceType}${log.resourceId ? `#${log.resourceId}` : ''}`;
          const href = linkFor(log);
          return href ? <Link className="audit-resource-link" to={href}>{text}</Link> : <Typography.Text>{text}</Typography.Text>;
        },
      },
      {
        title: 'Payload 预览',
        render: (_: unknown, log: AuditLog) => <button className="audit-payload-preview" type="button" onClick={() => setSelected(log)}>{preview(log)}</button>,
      },
    ],
    [],
  );

  const applyFilters = () => {
    setPage(1);
    setActive(filtersFromDraft(draft));
  };
  const resetFilters = () => {
    setDraft(emptyDraft);
    setPage(1);
    setActive({});
  };
  const errorText = logs.error instanceof AuditLogsQueryError && logs.error.status === 403 ? '权限不足' : (logs.error?.message ?? '审计日志加载失败。');

  return (
    <section className="audit-page" aria-label="Owner audit logs">
      <div className="page-heading">
        <div>
          <Typography.Title heading={3} className="page-title">审计日志</Typography.Title>
          <Typography.Text type="tertiary">查询所有 Owner 范围内的操作审计证据</Typography.Text>
        </div>
        <Button icon={<IconDownload />} loading={exportCsv.isPending} onClick={() => exportCsv.mutate(filters)}>导出 CSV</Button>
      </div>

      <div className="audit-filter-bar">
        <div className="audit-filter-grid">
          <Select multiple value={draft.actions} optionList={ACTION_OPTIONS} placeholder="操作类型" onChange={(value) => setDraft((old) => ({ ...old, actions: toArray(value) }))} />
          <Select multiple value={draft.resources} optionList={RESOURCE_OPTIONS} placeholder="资源类型" onChange={(value) => setDraft((old) => ({ ...old, resources: toArray(value) }))} />
          <Input value={draft.actor} placeholder="Actor user ID" onChange={(value) => setDraft((old) => ({ ...old, actor: onlyDigits(value) }))} />
          <Input value={draft.resource} placeholder="Resource ID" onChange={(value) => setDraft((old) => ({ ...old, resource: onlyDigits(value) }))} />
          <DatePicker type="dateTimeRange" value={draft.dates} placeholder={['开始时间', '结束时间']} onChange={(value) => setDraft((old) => ({ ...old, dates: toDates(value) }))} />
        </div>
        <Space className="audit-filter-actions">
          <Button icon={<IconSearch />} theme="solid" type="primary" onClick={applyFilters}>查询</Button>
          <Button onClick={resetFilters}>重置</Button>
          <Button icon={<IconRefresh />} onClick={() => logs.refetch()} loading={logs.isFetching}>刷新</Button>
        </Space>
      </div>

      <div className="task-toolbar">
        <Typography.Text type="tertiary">共 {logs.data?.total ?? 0} 条</Typography.Text>
        <Typography.Text type="tertiary">按时间倒序展示</Typography.Text>
      </div>

      <div className="task-table-surface">
        {logs.isLoading ? <div className="task-state-panel"><Spin size="large" /></div> : null}
        {logs.isError ? <div className="task-state-panel"><Empty title="审计日志加载失败" description={errorText} /><Button onClick={() => logs.refetch()}>重新加载</Button></div> : null}
        {!logs.isLoading && !logs.isError && items.length === 0 ? <div className="task-state-panel"><Empty title="未匹配任何审计记录" description="调整筛选条件后重新查询。" /><Button onClick={resetFilters}>重置筛选</Button></div> : null}
        {!logs.isLoading && !logs.isError && items.length > 0 ? (
          <>
            <Table className="audit-logs-table" columns={columns} dataSource={items} rowKey="id" pagination={false} />
            <div className="task-pagination">
              <Pagination total={logs.data?.total ?? 0} currentPage={page} pageSize={size} showSizeChanger onPageChange={setPage} onPageSizeChange={(next) => { setPage(1); setSize(next); }} />
            </div>
          </>
        ) : null}
      </div>

      <Modal title="Payload 详情" visible={Boolean(selected)} footer={null} width={720} onCancel={() => setSelected(null)}>
        {selected ? <div className="audit-payload-modal"><Typography.Text type="tertiary">payload_hash</Typography.Text><code>{selected.payloadHash}</code><pre>{fullPayload(selected)}</pre></div> : null}
      </Modal>
    </section>
  );
}
