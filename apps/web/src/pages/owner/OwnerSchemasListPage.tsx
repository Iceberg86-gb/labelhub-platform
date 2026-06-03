import { Button, Empty, Input, Space, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { LabelSchema } from '../../entities/schema/schemaTypes';
import { useSchemasQuery } from '../../features/schema-design/useSchemasQuery';
import { useSchemaVersionQuery } from '../../features/schema-design/useSchemaVersionQuery';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function CurrentVersionCell({ schema }: { schema: LabelSchema }) {
  const versionQuery = useSchemaVersionQuery(schema.id, schema.currentVersionId ?? null);

  if (!schema.currentVersionId) {
    return <Tag className="semantic-tag semantic-tag--neutral">未发布</Tag>;
  }

  if (versionQuery.isLoading) {
    return <Typography.Text type="tertiary">加载中</Typography.Text>;
  }

  return <Tag className="semantic-tag semantic-tag--accent">v{versionQuery.data?.versionNumber ?? schema.currentVersionId}</Tag>;
}

export function OwnerSchemasListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const q = searchParams.get('q')?.trim() || undefined;
  const schemasQuery = useSchemasQuery({ page, size, q });

  const updateParams = (patch: { page?: number; q?: string }) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(patch.page ?? page));
    next.set('size', String(size));
    if ('q' in patch) {
      const trimmed = patch.q?.trim();
      if (trimmed) next.set('q', trimmed);
      else next.delete('q');
    }
    setSearchParams(next);
  };

  const columns = useMemo(
    () => [
      {
        title: '模板（Schema）名称',
        dataIndex: 'name',
        render: (_: unknown, record: LabelSchema) => (
          <div className="schema-title-cell">
            <Typography.Text strong>{record.name}</Typography.Text>
            <Typography.Text type="tertiary">{record.description || '暂无描述'}</Typography.Text>
          </div>
        ),
      },
      {
        title: '当前版本',
        width: 120,
        render: (_: unknown, record: LabelSchema) => <CurrentVersionCell schema={record} />,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 150,
        render: (value?: string, record?: LabelSchema) => formatDateTime(value ?? record?.createdAt),
      },
      {
        title: '操作',
        width: 140,
        render: (_: unknown, record: LabelSchema) => (
          <Button
            icon={<IconEdit />}
            size="small"
            theme="borderless"
            onClick={() => navigate(`/owner/schemas/${record.id}/design`)}
          >
            进 Designer
          </Button>
        ),
      },
    ],
    [navigate],
  );

  const data = schemasQuery.data;
  const items = data?.items ?? [];
  const isEmpty = !schemasQuery.isLoading && !schemasQuery.isError && items.length === 0;

  return (
    <section className="schema-page" aria-label="Owner schema list">
      <div className="page-heading">
        <div>
          <Typography.Title heading={3} className="page-title">
            模板（Schema）管理
          </Typography.Title>
          <Typography.Text type="tertiary">查看任务绑定的模板（Schema），进入 Designer 发布不可变版本。</Typography.Text>
        </div>
        <Button icon={<IconRefresh />} onClick={() => schemasQuery.refetch()} loading={schemasQuery.isFetching}>
          刷新
        </Button>
      </div>

      <div className="task-toolbar">
        <Space>
          <Input
            prefix={<IconSearch />}
            placeholder="按名称或描述搜索"
            defaultValue={q}
            style={{ width: 260 }}
            onEnterPress={(event) => updateParams({ page: 1, q: event.currentTarget.value })}
          />
          <Button onClick={() => updateParams({ page: 1, q: undefined })}>清空</Button>
        </Space>
        <Typography.Text type="tertiary">共 {data?.total ?? 0} 个</Typography.Text>
      </div>

      <div className="task-table-surface">
        {schemasQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}

        {schemasQuery.isError ? (
          <div className="task-state-panel">
            <Empty
              title="模板（Schema）列表加载失败"
              description={schemasQuery.error instanceof Error ? schemasQuery.error.message : '请稍后重试。'}
            />
            <Button onClick={() => schemasQuery.refetch()}>重新加载</Button>
          </div>
        ) : null}

        {isEmpty ? (
          <div className="task-state-panel">
            <Empty title="暂无模板（Schema）" description="请先进入任务详情页，点击“去设计”创建并绑定模板（Schema）。" />
          </div>
        ) : null}

        {!schemasQuery.isLoading && !schemasQuery.isError && items.length > 0 ? (
          <Table columns={columns} dataSource={items} rowKey="id" pagination={false} />
        ) : null}
      </div>
    </section>
  );
}
