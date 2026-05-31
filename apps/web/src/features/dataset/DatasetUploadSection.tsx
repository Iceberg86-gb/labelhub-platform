import { Button, Empty, RadioGroup, Space, Spin, Table, Tag, Toast, Tooltip, Typography, Upload } from '@douyinfe/semi-ui';
import type { BeforeUploadProps, customRequestArgs } from '@douyinfe/semi-ui/lib/es/upload';
import { IconRefresh, IconUpload } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { DATASET_IMPORT_STATUS_LABELS, type Dataset, type DatasetImportFormat } from '../../entities/dataset/datasetTypes';
import type { Task } from '../task/list-tasks/useTasksQuery';
import { useDatasetsQuery } from './useDatasetsQuery';
import { UploadDatasetFailure, useUploadDatasetMutation } from './useUploadDatasetMutation';
import { UpdateCurrentDatasetFailure, useUpdateCurrentDatasetMutation } from './useUpdateCurrentDatasetMutation';

type DatasetUploadSectionProps = { task: Task };
type FormatChoice = 'auto' | DatasetImportFormat;
const MAX_FILE_SIZE = 10 * 1024 * 1024;
const formatOptions: Array<{ label: string; value: FormatChoice }> = [
  { label: '自动', value: 'auto' },
  { label: 'JSON', value: 'json' },
  { label: 'JSONL', value: 'jsonl' },
  { label: 'Excel', value: 'excel' },
];
const dateFormatter = new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });

export function DatasetUploadSection({ task }: DatasetUploadSectionProps) {
  const [formatChoice, setFormatChoice] = useState<FormatChoice>('auto');
  const datasetsQuery = useDatasetsQuery(task.id);
  const uploadDataset = useUploadDatasetMutation();
  const updateCurrentDataset = useUpdateCurrentDatasetMutation();
  const datasets = datasetsQuery.data?.items ?? [];
  const currentDataset = datasets.find((dataset) => dataset.id === task.currentDatasetId);
  const published = task.status === 'published';

  const columns = useMemo(
    () => [
      {
        title: '来源',
        dataIndex: 'sourceName',
        render: (value?: string, record?: Dataset) => (
          <div className="dataset-source-cell">
            <Typography.Text strong>{value || `Dataset #${record?.id ?? '-'}`}</Typography.Text>
            <Typography.Text type="tertiary">{formatDateTime(record?.createdAt)}</Typography.Text>
          </div>
        ),
      },
      { title: 'Item 数', dataIndex: 'itemCount', width: 100 },
      {
        title: '状态',
        dataIndex: 'importStatus',
        width: 110,
        render: (value: string) => (
          <Tag color={value === 'completed' ? 'green' : value === 'failed' ? 'red' : 'blue'}>
            {DATASET_IMPORT_STATUS_LABELS[value] ?? value}
          </Tag>
        ),
      },
      {
        title: '当前',
        width: 110,
        render: (_: unknown, record: Dataset) =>
          record.id === task.currentDatasetId ? <Tag color="green">当前数据集</Tag> : <Typography.Text type="tertiary">-</Typography.Text>,
      },
      {
        title: '操作',
        width: 130,
        render: (_: unknown, record: Dataset) => renderAction(record),
      },
    ],
    [published, task.currentDatasetId, updateCurrentDataset.isPending, updateCurrentDataset.variables?.datasetId],
  );

  function beforeUpload({ file }: BeforeUploadProps) {
    const fileInstance = file.fileInstance;
    const fileName = (fileInstance?.name ?? file.name).toLowerCase();
    if (fileInstance && fileInstance.size > MAX_FILE_SIZE) {
      Toast.error('文件过大,单次上传不能超过 10MB');
      return false;
    }
    if (!/\.(json|jsonl|xlsx)$/.test(fileName)) {
      Toast.error('仅支持 .json / .jsonl / .xlsx 格式');
      return false;
    }
    return true;
  }

  async function customRequest(request: customRequestArgs) {
    const format = formatChoice === 'auto' ? undefined : formatChoice;
    try {
      request.onProgress({ total: 100, loaded: 35 });
      const dataset = await uploadDataset.mutateAsync({
        taskId: task.id,
        file: request.fileInstance,
        sourceName: request.fileInstance.name,
        format,
      });
      request.onProgress({ total: 100, loaded: 100 });
      request.onSuccess(dataset);
      Toast.success(`已上传 dataset,${dataset.itemCount} 条数据`);
    } catch (error) {
      const failure = error instanceof UploadDatasetFailure ? error : null;
      request.onError({ status: failure?.status ?? 500 });
      Toast.error(failure?.userMessage ?? '上传失败,请稍后重试');
    }
  }

  function renderAction(record: Dataset) {
    if (record.id === task.currentDatasetId) {
      return <Tag color="green">已为当前</Tag>;
    }
    const button = (
      <Button
        size="small"
        disabled={published}
        loading={updateCurrentDataset.isPending && updateCurrentDataset.variables?.datasetId === record.id}
        onClick={() => setCurrentDataset(record.id)}
      >
        设为当前
      </Button>
    );
    return published ? <Tooltip content="任务已发布,无法切换"><span>{button}</span></Tooltip> : button;
  }

  async function setCurrentDataset(datasetId: number) {
    try {
      await updateCurrentDataset.mutateAsync({ taskId: task.id, datasetId });
      Toast.success('已设为当前数据集');
    } catch (error) {
      const failure = error instanceof UpdateCurrentDatasetFailure ? error : null;
      if (failure?.status === 409) {
        Toast.warning(failure.userMessage);
      } else {
        Toast.error(failure?.userMessage ?? '设置当前数据集失败,请稍后重试');
      }
    }
  }

  return (
    <div className="dataset-upload-section">
      <div className="dataset-section-header">
        <div>
          <Typography.Title heading={5}>数据集</Typography.Title>
          <Typography.Text type="tertiary">上传 JSON / JSONL / Excel 文件后,显式选择当前数据集。</Typography.Text>
        </div>
        <Tag color={task.currentDatasetId ? 'green' : 'grey'}>
          {task.currentDatasetId ? `当前: Dataset #${currentDataset?.id ?? task.currentDatasetId}` : '当前: 未设置'}
        </Tag>
      </div>

      <div className="dataset-upload-area">
        <Space vertical align="start" spacing={12}>
          <RadioGroup
            name="dataset-format" value={formatChoice} options={formatOptions} type="button" aria-label="数据集格式"
            onChange={(event) => setFormatChoice(event.target.value as FormatChoice)}
          />
          <Upload
            action="/datasets"
            accept=".json,.jsonl,.xlsx"
            limit={1}
            draggable
            showUploadList
            beforeUpload={beforeUpload}
            customRequest={customRequest}
            disabled={uploadDataset.isPending}
            dragIcon={<IconUpload />}
            dragMainText="上传数据集文件"
            dragSubText="支持 .json / .jsonl / .xlsx,单文件不超过 10MB"
          />
        </Space>
      </div>

      <div className="dataset-list-toolbar">
        <Typography.Text type="tertiary">共 {datasetsQuery.data?.total ?? 0} 个数据集</Typography.Text>
        <Button icon={<IconRefresh />} size="small" onClick={() => datasetsQuery.refetch()} loading={datasetsQuery.isFetching}>
          刷新
        </Button>
      </div>

      {datasetsQuery.isLoading ? <Spin /> : null}
      {datasetsQuery.isError ? (
        <Empty title="数据集列表加载失败" description={datasetsQuery.error instanceof Error ? datasetsQuery.error.message : '请稍后重试。'} />
      ) : null}
      {!datasetsQuery.isLoading && !datasetsQuery.isError && datasets.length === 0 ? (
        <Empty title="暂无数据集" description="上传第一个 JSON、JSONL 或 Excel 文件后,可将它设为当前数据集。" />
      ) : null}
      {datasets.length > 0 ? <Table columns={columns} dataSource={datasets} rowKey="id" pagination={false} /> : null}
    </div>
  );
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
