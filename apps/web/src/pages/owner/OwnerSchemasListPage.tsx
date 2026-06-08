import { Button, Empty, Input, Popconfirm, Space, Spin, Table, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconDownload, IconEdit, IconRefresh, IconSearch, IconUpload } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { LabelSchema } from '../../entities/schema/schemaTypes';
import { downloadSchemaPackage, exportSchemaVersionPackage } from '../../features/schema-design/schemaExport';
import { SchemaImportModal } from '../../features/schema-design/SchemaImportModal';
import { useArchiveSchemaTemplateMutation } from '../../features/schema-design/useArchiveSchemaTemplateMutation';
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
  const archiveTemplate = useArchiveSchemaTemplateMutation();
  const [importVisible, setImportVisible] = useState(false);
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

  const handleExport = async (record: LabelSchema) => {
    if (!record.currentVersionId) {
      Toast.warning('该模板还没有可导出的版本。');
      return;
    }

    try {
      const pkg = await exportSchemaVersionPackage(record.id, record.currentVersionId);
      downloadSchemaPackage(pkg);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '模板导出失败。');
    }
  };

  const handleArchive = async (record: LabelSchema) => {
    try {
      await archiveTemplate.mutateAsync(record.id);
      Toast.success('模板已删除。');
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String(error.message) : '模板删除失败。';
      Toast.error(message);
    }
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
        title: '类型',
        width: 110,
        align: 'center' as const,
        render: (_: unknown, record: LabelSchema) => (
          record.taskId == null
            ? <Tag className="semantic-tag semantic-tag--success">模板库</Tag>
            : <Tag className="semantic-tag semantic-tag--neutral">任务绑定</Tag>
        ),
      },
      {
        title: '当前版本',
        width: 120,
        align: 'center' as const,
        render: (_: unknown, record: LabelSchema) => <CurrentVersionCell schema={record} />,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 150,
        align: 'center' as const,
        render: (value?: string, record?: LabelSchema) => formatDateTime(value ?? record?.createdAt),
      },
      {
        title: '操作',
        width: 300,
        align: 'center' as const,
        render: (_: unknown, record: LabelSchema) => (
          <Space className="schema-action-cell">
            <Button
              icon={<IconEdit />}
              size="small"
              theme="borderless"
              onClick={() => navigate(`/owner/schemas/${record.id}/design`)}
            >
              进 Designer
            </Button>
            <Button
              icon={<IconDownload />}
              size="small"
              theme="borderless"
              disabled={!record.currentVersionId}
              onClick={() => handleExport(record)}
            >
              导出
            </Button>
            {record.taskId == null ? (
              <Popconfirm
                title="删除该模板？"
                content="只会从模板库移除，不影响已创建任务和历史提交。"
                okText="删除"
                cancelText="取消"
                position="bottomRight"
                onConfirm={() => handleArchive(record)}
              >
                <Button
                  icon={<IconDelete />}
                  size="small"
                  theme="borderless"
                  type="danger"
                  loading={archiveTemplate.isPending}
                >
                  删除
                </Button>
              </Popconfirm>
            ) : (
              <Tooltip content="任务绑定 Schema 暂不从这里删除。">
                <Button icon={<IconDelete />} size="small" theme="borderless" type="danger" disabled>
                  删除
                </Button>
              </Tooltip>
            )}
          </Space>
        ),
      },
    ],
    [archiveTemplate.isPending, navigate],
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
          <Typography.Text type="tertiary">管理可复用模板和任务绑定 Schema，进入 Designer 发布不可变版本。</Typography.Text>
        </div>
        <Space>
          <Button icon={<IconUpload />} onClick={() => setImportVisible(true)}>
            导入模板
          </Button>
          <Button icon={<IconRefresh />} onClick={() => schemasQuery.refetch()} loading={schemasQuery.isFetching}>
            刷新
          </Button>
        </Space>
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
            <Empty title="暂无模板（Schema）" description="可以从 JSON 导入模板，或在任务详情页进入 Designer 创建任务绑定 Schema。" />
          </div>
        ) : null}

        {!schemasQuery.isLoading && !schemasQuery.isError && items.length > 0 ? (
          <Table columns={columns} dataSource={items} rowKey="id" pagination={false} />
        ) : null}
      </div>
      <SchemaImportModal visible={importVisible} onClose={() => setImportVisible(false)} />
    </section>
  );
}
