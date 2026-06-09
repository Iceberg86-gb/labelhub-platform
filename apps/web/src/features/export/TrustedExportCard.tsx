import { Button, Card, Checkbox, Empty, Input, Pagination, Popconfirm, Select, Space, Spin, Table, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import {
  IconArchive,
  IconChevronDown,
  IconChevronUp,
  IconConfigStroked,
  IconDelete,
  IconDownload,
  IconInfoCircle,
  IconList,
  IconRefresh,
  IconServer,
  IconShield,
  IconUpload,
  IconUser,
} from '@douyinfe/semi-icons';
import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type {
  ExportFieldCatalog,
  ExportFieldMapping,
  ExportSnapshot,
  TrainingExportFormat,
  TrainingExportProfile,
} from '../../entities/export/exportTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { CreateExportFailure, useCreateExportMutation } from './useCreateExportMutation';
import { ExportSnapshotDiffModal } from './ExportSnapshotDiffModal';
import { useTaskExportsQuery } from './useTaskExportsQuery';
import { useTaskExportFieldsQuery } from './useTaskExportFieldsQuery';
import { useDownloadExportPackageMutation } from './useDownloadExportPackageMutation';
import { useArchiveExportSnapshotMutation } from './useArchiveExportSnapshotMutation';
import {
  buildFieldLabelMap,
  buildFieldOptions,
  buildMappingRowsFromCatalog,
  computeBindingPreview,
  truncate,
  type BindingPreviewResult,
  type FieldOption,
} from './exportBindingPreview';

type TrustedExportCardProps = {
  taskId: number;
};

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;
const DEFAULT_FIELD_MAPPING_ROWS: FieldMappingDraftRow[] = [
  { id: 'task_id', source: 'task_id', columnName: 'task_id', included: true },
  { id: 'dataset_item_id', source: 'dataset_item_id', columnName: 'dataset_item_id', included: true },
  { id: 'submission_id', source: 'submission_id', columnName: 'submission_id', included: true },
  { id: 'schema_version_id', source: 'schema_version_id', columnName: 'schema_version_id', included: true },
  { id: 'submitted_at', source: 'submitted_at', columnName: 'submitted_at', included: true },
  { id: 'final_verdict', source: 'final_verdict', columnName: 'final_verdict', included: true },
  { id: 'reviewed_at', source: 'reviewed_at', columnName: 'reviewed_at', included: true },
  { id: 'item-source', source: 'item.text', columnName: 'item_text', included: true },
  { id: 'answer-source', source: 'answer.title', columnName: 'answer_title', included: true },
];

const DEFAULT_TRAINING_PROFILE: TrainingProfileDraft = {
  promptSource: 'item.prompt',
  completionSource: 'answer.summary',
  preferenceSource: 'answer.preferred',
  choiceASource: 'item.response_a',
  choiceBSource: 'item.response_b',
};

const TRAINING_FORMAT_OPTIONS: Array<{ value: TrainingExportFormat; label: string }> = [
  { value: 'flat_table', label: '表格快照' },
  { value: 'openai_chat_sft_jsonl', label: '对话微调' },
  { value: 'trl_sft_jsonl', label: '指令微调' },
  { value: 'trl_dpo_jsonl', label: '偏好对比训练' },
];

const TRAINING_FORMAT_COPY: Record<TrainingExportFormat, string> = {
  flat_table: '生成可信表格快照,用于复核、审计和离线分析。',
  openai_chat_sft_jsonl: '生成对话微调数据,一条记录对应一轮用户提示与助手回答。',
  trl_sft_jsonl: '生成指令微调数据,一条记录对应提示与答案。',
  trl_dpo_jsonl: '生成偏好训练数据,一条记录对应提示、优选答案和拒绝答案。',
};

const TRAINING_FORMAT_DETAIL: Record<
  TrainingExportFormat,
  { title: string; fileName: string; audience: string; structure: string; requiredFields: string[] }
> = {
  flat_table: {
    title: '表格快照',
    fileName: '可信表格快照',
    audience: '适合人工复核、审计交付和离线分析。',
    structure: '系统溯源字段 + 业务内容字段',
    requiredFields: ['无需额外绑定'],
  },
  openai_chat_sft_jsonl: {
    title: '对话微调',
    fileName: '对话微调数据',
    audience: '适合对话式监督微调数据准备。',
    structure: '用户提示 → 助手回答',
    requiredFields: ['用户提示', '助手回答'],
  },
  trl_sft_jsonl: {
    title: '指令微调',
    fileName: '指令微调数据',
    audience: '适合提示与答案形式的监督微调。',
    structure: '用户提示 → 标准答案',
    requiredFields: ['用户提示', '标准答案'],
  },
  trl_dpo_jsonl: {
    title: '偏好对比训练',
    fileName: '偏好对比数据',
    audience: '适合偏好对比训练,生成优选回答与拒绝回答。',
    structure: '用户提示 → 优选回答 / 拒绝回答',
    requiredFields: ['用户提示', '偏好字段', '回答 A', '回答 B'],
  },
};

const SYSTEM_FIELD_META: Record<string, { label: string; description: string }> = {
  task_id: { label: '任务 ID', description: '任务在系统中的唯一标识' },
  dataset_item_id: { label: '数据项 ID', description: '该条数据在数据集中的唯一标识' },
  submission_id: { label: '提交 ID', description: '标注员提交记录的唯一标识' },
  schema_version_id: { label: 'Schema 版本', description: '本条数据绑定的标注模板版本' },
  submitted_at: { label: '提交时间', description: '标注员提交答案的时间' },
  final_verdict: { label: '终审结论', description: '审核流程的最终裁决结果' },
  reviewed_at: { label: '审核时间', description: '终审完成的时间' },
};

const EXPORT_FLOW_STEPS = [
  { label: '配置字段', icon: <IconConfigStroked /> },
  { label: '导出', icon: <IconUpload /> },
  { label: '快照入列', icon: <IconList /> },
  { label: '对比 / 下载', icon: <IconDownload /> },
];

type FieldMappingDraftRow = {
  id: string;
  source: string;
  columnName: string;
  included: boolean;
};

type TrainingProfileDraft = {
  promptSource: string;
  completionSource: string;
  preferenceSource: string;
  choiceASource: string;
  choiceBSource: string;
};

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function TrustedExportCard({ taskId }: TrustedExportCardProps) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [mappingRows, setMappingRows] = useState<FieldMappingDraftRow[]>(DEFAULT_FIELD_MAPPING_ROWS);
  const [trainingFormat, setTrainingFormat] = useState<TrainingExportFormat>('flat_table');
  const [trainingProfile, setTrainingProfile] = useState<TrainingProfileDraft>(DEFAULT_TRAINING_PROFILE);
  const [mappingExpanded, setMappingExpanded] = useState(false);
  const [diffModalOpen, setDiffModalOpen] = useState(false);
  const [archivingSnapshotId, setArchivingSnapshotId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('exportPage')) ?? DEFAULT_PAGE;
  const size = parsePositiveInt(searchParams.get('exportSize')) ?? DEFAULT_SIZE;
  const showArchived = searchParams.get('exportArchived') === 'true';
  const exportsQuery = useTaskExportsQuery(taskId, { page, size, archived: showArchived });
  const exportFieldsQuery = useTaskExportFieldsQuery(taskId);
  const catalog = exportFieldsQuery.data;
  const createExport = useCreateExportMutation();
  const downloadExportPackage = useDownloadExportPackageMutation();
  const archiveExportSnapshot = useArchiveExportSnapshotMutation();
  const fieldOptions = useMemo(() => buildFieldOptions(catalog), [catalog]);
  const fieldLabelMap = useMemo(() => buildFieldLabelMap(catalog), [catalog]);
  const prefilledRef = useRef(false);
  useEffect(() => {
    if (!catalog || prefilledRef.current) {
      return;
    }
    prefilledRef.current = true;
    const recommended = catalog.recommendedFormat;
    if (recommended && recommended !== 'flat_table') {
      setTrainingFormat((current) => (current === 'flat_table' ? recommended : current));
    }
    const bindings = catalog.recommendedBindings;
    if (bindings) {
      setTrainingProfile((current) => ({
        ...current,
        promptSource: bindings.promptSource ?? current.promptSource,
        completionSource: bindings.completionSource ?? current.completionSource,
        preferenceSource: bindings.preferenceSource ?? current.preferenceSource,
        choiceASource: bindings.choiceSources?.A ?? current.choiceASource,
        choiceBSource: bindings.choiceSources?.B ?? current.choiceBSource,
      }));
    }
    const catalogRows = buildMappingRowsFromCatalog(catalog);
    if (catalogRows.length > 0) {
      setMappingRows(catalogRows);
    }
  }, [catalog]);
  const bindingPreview = useMemo(
    () => computeBindingPreview(trainingFormat, trainingProfile, catalog?.sampleRows ?? []),
    [trainingFormat, trainingProfile, catalog],
  );
  const trainingExportBlocked =
    trainingFormat !== 'flat_table' && bindingPreview.total > 0 && bindingPreview.validCount === 0;
  const items = exportsQuery.data?.items ?? [];
  const manifestHashCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      counts.set(item.manifestHash, (counts.get(item.manifestHash) ?? 0) + 1);
    }
    return counts;
  }, [items]);
  const canDiff = selectedIds.length === 2;
  const baseSnapshotId = canDiff ? selectedIds[0] ?? null : null;
  const compareSnapshotId = canDiff ? selectedIds[1] ?? null : null;
  const groupedMappingRows = groupMappingRows(mappingRows);
  const mappingSummary = summarizeMappingRows(mappingRows);
  const trainingFormatDetail = TRAINING_FORMAT_DETAIL[trainingFormat];
  const trainingBindingNote =
    trainingFormat === 'flat_table'
      ? '表格快照使用下方高级字段映射生成 CSV 与 Excel。'
      : '缺少必填字段的记录会自动跳过,不写入训练数据。';

  const columns = useMemo(
    () => [
      {
        title: '选择',
        width: 70,
        render: (_: unknown, record: ExportSnapshot) => (
          <Checkbox checked={selectedIds.includes(record.id)} onChange={() => toggleSelection(record.id)} />
        ),
      },
      { title: 'Snapshot', dataIndex: 'id', width: 110, render: (value: number) => `#${value}` },
      {
        title: 'Manifest Hash',
        dataIndex: 'manifestHash',
        render: (value: string) => (
          <span className="export-manifest-hash-cell">
            <TruncatedHash value={value} ariaLabel="Export manifest hash" />
            {(manifestHashCounts.get(value) ?? 0) > 1 ? <Tag className="export-same-hash-tag">✓ 同 hash</Tag> : null}
          </span>
        ),
      },
      {
        title: '提交数',
        width: 100,
        render: (_: unknown, record: ExportSnapshot) => record.recordCounts?.submissions ?? 0,
      },
      {
        title: '生成时间',
        dataIndex: 'generatedAt',
        width: 150,
        render: (value: string) => formatDateTime(value),
      },
      ...(showArchived
        ? [
            {
              title: '归档时间',
              dataIndex: 'archivedAt',
              width: 150,
              render: (value?: string) => formatDateTime(value),
            },
          ]
        : []),
      {
        title: '下载',
        width: 220,
        render: (_: unknown, record: ExportSnapshot) => (
          <Space spacing="tight" wrap>
            <Button
              size="small"
              loading={downloadExportPackage.isPending}
              onClick={() => downloadExportPackage.mutate({ snapshotId: record.id, packageType: 'annotation_results' })}
            >
              标注包
            </Button>
            {hasTrainingPackage(record) ? (
              <Button
                size="small"
                loading={downloadExportPackage.isPending}
                onClick={() => downloadExportPackage.mutate({ snapshotId: record.id, packageType: 'training_data' })}
              >
                训练包
              </Button>
            ) : null}
          </Space>
        ),
      },
      ...(showArchived
        ? []
        : [
            {
              title: '操作',
              width: 100,
              render: (_: unknown, record: ExportSnapshot) => (
                <Popconfirm
                  title="归档导出快照?"
                  content={
                    <Typography.Text style={{ display: 'block', maxWidth: 340, lineHeight: 1.6 }}>
                      归档后该快照会从默认列表隐藏,但快照记录和导出文件仍会保留,可在已归档视图中下载审计。
                    </Typography.Text>
                  }
                  position="leftTop"
                  okText="归档"
                  cancelText="取消"
                  okType="warning"
                  onConfirm={() => handleArchiveSnapshot(record)}
                >
                  <Button icon={<IconArchive />} size="small" theme="borderless" loading={archivingSnapshotId === record.id}>
                    归档
                  </Button>
                </Popconfirm>
              ),
            },
          ]),
    ],
    [archiveExportSnapshot, archivingSnapshotId, downloadExportPackage, manifestHashCounts, selectedIds, showArchived, taskId],
  );

  async function handleCreateAnnotationExport() {
    try {
      await createExport.mutateAsync({
        taskId,
        fieldMapping: buildFieldMapping(mappingRows),
        trainingFormat: 'flat_table',
        trainingProfile: undefined,
      });
      Toast.success('标注结果包快照已提交');
    } catch (error) {
      const failure = error instanceof CreateExportFailure ? error : null;
      Toast.error(failure?.userMessage ?? '导出失败');
    }
  }

  async function handleCreateTrainingExport() {
    if (trainingFormat === 'flat_table') {
      Toast.warning('请先选择训练格式');
      return;
    }
    try {
      await createExport.mutateAsync({
        taskId,
        fieldMapping: buildFieldMapping(mappingRows),
        trainingFormat,
        trainingProfile: buildTrainingProfile(trainingFormat, trainingProfile),
      });
      Toast.success('训练数据包快照已提交');
    } catch (error) {
      const failure = error instanceof CreateExportFailure ? error : null;
      Toast.error(failure?.userMessage ?? '导出失败');
    }
  }

  function updateMappingRow(rowId: string, patch: Partial<FieldMappingDraftRow>) {
    setMappingRows((current) => current.map((row) => (row.id === rowId ? { ...row, ...patch } : row)));
  }

  function updateTrainingProfile(patch: Partial<TrainingProfileDraft>) {
    setTrainingProfile((current) => ({ ...current, ...patch }));
  }

  function applyRecommendation() {
    if (!catalog?.recommendedFormat || catalog.recommendedFormat === 'flat_table') {
      return;
    }
    setTrainingFormat(catalog.recommendedFormat);
    const bindings = catalog.recommendedBindings;
    if (bindings) {
      setTrainingProfile((current) => ({
        ...current,
        promptSource: bindings.promptSource ?? current.promptSource,
        completionSource: bindings.completionSource ?? current.completionSource,
        preferenceSource: bindings.preferenceSource ?? current.preferenceSource,
        choiceASource: bindings.choiceSources?.A ?? current.choiceASource,
        choiceBSource: bindings.choiceSources?.B ?? current.choiceBSource,
      }));
    }
  }

  function addMappingRow() {
    const id = `custom-${Date.now()}`;
    setMappingRows((current) => [...current, { id, source: '', columnName: '', included: true }]);
  }

  function removeMappingRow(rowId: string) {
    setMappingRows((current) => current.filter((row) => row.id !== rowId));
  }

  function toggleSelection(snapshotId: number) {
    setSelectedIds((current) => {
      if (current.includes(snapshotId)) return current.filter((id) => id !== snapshotId);
      if (current.length >= 2) return [current[1], snapshotId];
      return [...current, snapshotId];
    });
  }

  async function handleArchiveSnapshot(snapshot: ExportSnapshot) {
    setArchivingSnapshotId(snapshot.id);
    try {
      await archiveExportSnapshot.mutateAsync({ taskId, snapshotId: snapshot.id });
      setSelectedIds((current) => current.filter((id) => id !== snapshot.id));
      Toast.success(`已归档导出快照 #${snapshot.id}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '归档失败,请稍后重试');
    } finally {
      setArchivingSnapshotId(null);
    }
  }

  function updateParams(next: { page?: number; size?: number; archived?: boolean }) {
    const params = new URLSearchParams(searchParams);
    params.set('exportPage', String(next.page ?? page));
    params.set('exportSize', String(next.size ?? size));
    params.set('exportArchived', String(next.archived ?? showArchived));
    setSearchParams(params);
  }

  return (
    <Card className="trusted-export-card trusted-export-card--console" bordered={false}>
      <div className="trusted-export-header trusted-export-console-hero">
        <div className="trusted-export-console-hero__copy">
          <Typography.Title className="trusted-export-title" heading={4}>
            可信快照控制台
          </Typography.Title>
          <Typography.Text className="trusted-export-subtitle">
            将 task 的 source facts 物化为 canonical export 快照,用于训练数据交付与审计复现。
          </Typography.Text>
          <Typography.Text type="tertiary">导出仅包含审核通过(approved)的可信数据,通过即视为入库。</Typography.Text>
        </div>
        <div className="trusted-export-status-strip" aria-label="Export reproducibility summary">
          <span className="trusted-export-status-pill trusted-export-status-pill--strong">
            <span className="trusted-export-status-pill__icon"><IconServer /></span>
            <span className="trusted-export-status-pill__body">
              <strong>{exportsQuery.data?.total ?? 0} 个{showArchived ? '已归档' : '活跃'}快照</strong>
              <small>当前任务已生成的活跃快照总数</small>
            </span>
          </span>
          <span className="trusted-export-status-pill">
            <span className="trusted-export-status-pill__icon"><IconUser /></span>
            <span className="trusted-export-status-pill__body">
              <strong>已选 {selectedIds.length}/2</strong>
              <small>最多同时选择 2 个快照用于对比</small>
            </span>
          </span>
          <span className="trusted-export-status-pill trusted-export-status-pill--stable">
            <span className="trusted-export-status-pill__icon"><IconShield /></span>
            <span className="trusted-export-status-pill__body">
              <strong>可复现</strong>
              <small>所有快照均基于相同规范与数据生成</small>
            </span>
          </span>
        </div>
      </div>

      <div className="trusted-export-flow-strip" aria-label="Export workflow">
        {EXPORT_FLOW_STEPS.map((step, index) => (
          <Fragment key={step.label}>
            <span className="trusted-export-flow-step">
              <span className="trusted-export-flow-step__icon">{step.icon}</span>
              <span>{step.label}</span>
            </span>
            {index < EXPORT_FLOW_STEPS.length - 1 ? <span className="trusted-export-flow-connector" aria-hidden="true" /> : null}
          </Fragment>
        ))}
      </div>

      <div className="trusted-export-toolbar trusted-export-command-strip">
        <Typography.Text className="trusted-export-snapshot-count" type="tertiary">
          共 {exportsQuery.data?.total ?? 0} 个{showArchived ? '已归档' : '活跃'}快照
          {selectedIds.length > 0 ? `,已选 ${selectedIds.length}/2` : ''}
        </Typography.Text>
        <Space className="trusted-export-command-group">
          <Select
            size="small"
            value={showArchived ? 'archived' : 'active'}
            onChange={(value) => {
              setSelectedIds([]);
              updateParams({ page: 1, archived: value === 'archived' });
            }}
            optionList={[
              { value: 'active', label: '活跃快照' },
              { value: 'archived', label: '已归档快照' },
            ]}
          />
          <Button icon={<IconRefresh />} disabled={!canDiff} onClick={() => setDiffModalOpen(true)}>
            对比所选
          </Button>
          <Button
            icon={<IconUpload />}
            theme="solid"
            type="primary"
            loading={createExport.isPending && trainingFormat === 'flat_table'}
            onClick={handleCreateAnnotationExport}
          >
            导出标注结果包
          </Button>
          <Tooltip
            content={
              trainingFormat === 'flat_table'
                ? '请先在下方「训练格式详情」中选择一种训练格式(如 OpenAI 对话微调 / TRL 指令微调 / TRL 偏好训练),再导出训练数据包;表格快照只需用「导出标注结果包」。'
                : '当前字段绑定在样本数据中产生 0 行有效记录,请在下方预览中检查绑定(必填字段为空或偏好取值不匹配回答)。'
            }
            trigger={trainingFormat === 'flat_table' || trainingExportBlocked ? 'hover' : 'custom'}
            visible={trainingFormat === 'flat_table' || trainingExportBlocked ? undefined : false}
          >
            <span className="trusted-export-training-export-trigger">
              <Button
                icon={<IconUpload />}
                disabled={trainingFormat === 'flat_table' || trainingExportBlocked}
                loading={createExport.isPending && trainingFormat !== 'flat_table'}
                onClick={handleCreateTrainingExport}
              >
                导出训练数据包
              </Button>
            </span>
          </Tooltip>
        </Space>
      </div>

      <div className="trusted-export-console-grid">
        <div className="trusted-export-field-mapping trusted-export-builder">
          <div className="trusted-export-training-detail">
            <div className="trusted-export-training-detail__header">
              <div>
                <Typography.Text strong>训练格式详情</Typography.Text>
                <Typography.Text type="tertiary">{TRAINING_FORMAT_COPY[trainingFormat]}</Typography.Text>
              </div>
              <Select
                className="trusted-export-training-profile__select"
                size="small"
                value={trainingFormat}
                onChange={(value) => setTrainingFormat(value as TrainingExportFormat)}
                optionList={TRAINING_FORMAT_OPTIONS}
              />
            </div>
            {catalog?.recommendedFormat && catalog.recommendedFormat !== 'flat_table' ? (
              <div className="trusted-export-recommendation">
                <Tag color="blue" className="trusted-export-recommendation__tag">
                  推荐 {formatLabel(catalog.recommendedFormat)}
                </Tag>
                <Typography.Text type="tertiary" className="trusted-export-recommendation__reason">
                  {catalog.recommendationReason}
                </Typography.Text>
                {trainingFormat !== catalog.recommendedFormat ? (
                  <Button size="small" theme="borderless" type="primary" onClick={applyRecommendation}>
                    采用推荐
                  </Button>
                ) : null}
              </div>
            ) : null}
              <div className="trusted-export-training-detail__body">
                <div className="trusted-export-training-detail__summary">
                <Typography.Text strong>{trainingFormatDetail.fileName}</Typography.Text>
                <Typography.Text type="tertiary">{trainingFormatDetail.audience}</Typography.Text>
              </div>
              <div className="trusted-export-training-schema-preview">
                <span>每行结构</span>
                <code>{trainingFormatDetail.structure}</code>
              </div>
            </div>
            <div className="trusted-export-training-bindings">
              <div className="trusted-export-training-bindings__head">
                <div className="trusted-export-training-bindings__title">
                  <Typography.Text strong>字段绑定</Typography.Text>
                  <Typography.Text type="tertiary">{trainingBindingNote}</Typography.Text>
                </div>
                {trainingFormat === 'flat_table' ? (
                  <div className="trusted-export-training-bindings__required">
                    {trainingFormatDetail.requiredFields.map((field) => (
                      <Tag key={field} size="small">{field}</Tag>
                    ))}
                  </div>
                ) : null}
              </div>
              {trainingFormat !== 'flat_table' ? (
                <div className="trusted-export-training-profile__fields">
                  <FieldBindingSelect
                    label="用户提示"
                    value={trainingProfile.promptSource}
                    options={fieldOptions}
                    onChange={(promptSource) => updateTrainingProfile({ promptSource })}
                  />
                  {trainingFormat === 'trl_dpo_jsonl' ? (
                    <>
                      <FieldBindingSelect
                        label="偏好字段"
                        value={trainingProfile.preferenceSource}
                        options={fieldOptions}
                        onChange={(preferenceSource) => updateTrainingProfile({ preferenceSource })}
                      />
                      <FieldBindingSelect
                        label="回答 A"
                        value={trainingProfile.choiceASource}
                        options={fieldOptions}
                        onChange={(choiceASource) => updateTrainingProfile({ choiceASource })}
                      />
                      <FieldBindingSelect
                        label="回答 B"
                        value={trainingProfile.choiceBSource}
                        options={fieldOptions}
                        onChange={(choiceBSource) => updateTrainingProfile({ choiceBSource })}
                      />
                    </>
                  ) : (
                    <FieldBindingSelect
                      label="助手回答"
                      value={trainingProfile.completionSource}
                      options={fieldOptions}
                      onChange={(completionSource) => updateTrainingProfile({ completionSource })}
                    />
                  )}
                </div>
              ) : null}
              {trainingFormat !== 'flat_table' ? (
                <BindingPreview
                  preview={bindingPreview}
                  fieldLabels={fieldLabelMap}
                  loading={exportFieldsQuery.isLoading}
                  hasCatalog={Boolean(catalog)}
                />
              ) : null}
            </div>
          </div>

          <div className="trusted-export-mapping-panel">
            <div className="trusted-export-mapping-summary">
              <div className="trusted-export-mapping-summary__copy">
                <Typography.Text strong>高级字段映射</Typography.Text>
                <Typography.Text type="tertiary">
                  已启用 {mappingSummary.enabled} 列 · 系统字段 {mappingSummary.system} · 业务字段 {mappingSummary.content}
                </Typography.Text>
              </div>
              <Space>
                <Button size="small" onClick={() => setMappingExpanded((expanded) => !expanded)} icon={mappingExpanded ? <IconChevronUp /> : <IconChevronDown />}>
                  {mappingExpanded ? '收起编辑' : '展开编辑'}
                </Button>
                {mappingExpanded ? (
                  <Button size="small" onClick={addMappingRow}>
                    添加列
                  </Button>
                ) : null}
              </Space>
            </div>
            {mappingExpanded ? (
              <div className="trusted-export-builder__table">
                <div className="trusted-export-builder__table-head">
                  <div className="trusted-export-builder__table-head-grid">
                    <span />
                    <span className="trusted-export-builder__heading">源字段</span>
                    <span className="trusted-export-builder__heading trusted-export-builder__heading--column">导出列名</span>
                    <span />
                  </div>
                </div>
                <div className="trusted-export-field-mapping-list trusted-export-builder__list">
                  {groupedMappingRows.map((group) => (
                    <div className="trusted-export-builder__group" key={group.kind}>
                      <div className="trusted-export-builder__group-label">{group.label}</div>
                      <div className="trusted-export-builder__group-rows">
                        {group.rows.map((row) => (
                          <div className="trusted-export-mapping-row" key={row.id}>
                            <Checkbox checked={row.included} onChange={(event) => updateMappingRow(row.id, { included: Boolean(event.target.checked) })} />
                            <SourceFieldCell row={row} options={fieldOptions} onChange={(patch) => updateMappingRow(row.id, patch)} />
                            <Input
                              className="trusted-export-mapping-row__column-input"
                              size="small"
                              value={row.columnName}
                              placeholder="导出列名"
                              onChange={(value) => updateMappingRow(row.id, { columnName: value })}
                            />
                            <Button icon={<IconDelete />} size="small" type="danger" theme="borderless" onClick={() => removeMappingRow(row.id)} />
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="trusted-export-snapshot-panel">
          {exportsQuery.isLoading ? <Spin /> : null}
          {exportsQuery.isError ? (
            <div className="trusted-export-empty-state">
              <Empty title="导出列表加载失败" description={exportsQuery.error instanceof Error ? exportsQuery.error.message : '请稍后重试。'} />
            </div>
          ) : null}
          {!exportsQuery.isLoading && !exportsQuery.isError && items.length === 0 ? (
            <div className="trusted-export-empty-state trusted-export-empty-state--snapshot">
              <Empty
                title={showArchived ? '暂无已归档快照' : '尚未导出'}
                description={showArchived ? '归档后的快照会保留在这里,仍可下载审计。' : '点击"导出"按钮创建可信训练数据集快照。'}
              />
            </div>
          ) : null}
          {items.length > 0 ? (
            <div className="trusted-export-table-shell">
              <Table className="trusted-export-table" columns={columns} dataSource={items} rowKey="id" pagination={false} size="small" />
              <div className="export-pagination">
                <Pagination
                  total={exportsQuery.data?.total ?? 0}
                  currentPage={page}
                  pageSize={size}
                  showSizeChanger
                  onPageChange={(nextPage) => updateParams({ page: nextPage })}
                  onPageSizeChange={(nextSize) => updateParams({ page: 1, size: nextSize })}
                />
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {diffModalOpen && baseSnapshotId != null && compareSnapshotId != null ? (
        <ExportSnapshotDiffModal baseSnapshotId={baseSnapshotId} compareSnapshotId={compareSnapshotId} onClose={() => setDiffModalOpen(false)} />
      ) : null}
    </Card>
  );
}

function SourceFieldCell({
  row,
  options,
  onChange,
}: {
  row: FieldMappingDraftRow;
  options: FieldOption[];
  onChange: (patch: Partial<FieldMappingDraftRow>) => void;
}) {
  const meta = SYSTEM_FIELD_META[row.source];
  if (!meta) {
    return (
      <span className="trusted-export-mapping-row__source trusted-export-mapping-row__source--custom">
        <Select
          className="trusted-export-mapping-row__source-select"
          size="small"
          value={row.source || undefined}
          optionList={options}
          filter
          allowCreate
          showClear
          placeholder="source: item.prompt / answer.label"
          onChange={(source) => onChange({ source: String(source ?? '') })}
        />
      </span>
    );
  }

  return (
    <span className="trusted-export-mapping-row__source trusted-export-mapping-row__source--system">
      <span className="trusted-export-mapping-row__source-label">
        {meta.label}
        <Tooltip content={meta.description}>
          <IconInfoCircle size="small" />
        </Tooltip>
      </span>
      <code className="trusted-export-mapping-row__source-code">{row.source}</code>
    </span>
  );
}

function FieldBindingSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: FieldOption[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="trusted-export-training-source">
      <span>{label}</span>
      <Select
        className="trusted-export-training-source__select"
        size="small"
        value={value || undefined}
        optionList={options}
        filter
        allowCreate
        showClear
        placeholder="选择字段"
        onChange={(next) => onChange(String(next ?? ''))}
      />
    </label>
  );
}

function BindingPreview({
  preview,
  fieldLabels,
  loading,
  hasCatalog,
}: {
  preview: BindingPreviewResult;
  fieldLabels: Record<string, string>;
  loading: boolean;
  hasCatalog: boolean;
}) {
  if (loading) {
    return <div className="trusted-export-binding-preview trusted-export-binding-preview--empty">加载样本数据…</div>;
  }
  if (!hasCatalog || preview.total === 0) {
    return (
      <div className="trusted-export-binding-preview trusted-export-binding-preview--empty">
        暂无已审核样本可预览(需要至少 1 条审核通过的提交)。
      </div>
    );
  }
  const ok = preview.validCount > 0;
  return (
    <div className="trusted-export-binding-preview">
      <div className="trusted-export-binding-preview__caption">第 1 行将生成 →</div>
      <div className="trusted-export-binding-preview__rows">
        {preview.slots.map((slot) => (
          <div className="trusted-export-binding-preview__row" key={slot.label}>
            <span className={`trusted-export-binding-preview__slot trusted-export-binding-preview__slot--${slot.tone}`}>
              {slot.label}
            </span>
            <span className="trusted-export-binding-preview__source">{slot.source ? fieldLabels[slot.source] ?? slot.source : '未绑定'}</span>
            <span className="trusted-export-binding-preview__value">{slot.value ? truncate(slot.value, 48) : '(空)'}</span>
          </div>
        ))}
      </div>
      <div className={`trusted-export-binding-preview__verdict ${ok ? 'is-ok' : 'is-bad'}`}>
        {ok
          ? `预计可用 · 样本 ${preview.validCount}/${preview.total} 行可生成`
          : `样本 ${preview.total} 行均无法生成,请检查字段绑定`}
      </div>
    </div>
  );
}

function formatLabel(format: TrainingExportFormat) {
  return TRAINING_FORMAT_OPTIONS.find((option) => option.value === format)?.label ?? format;
}

function groupMappingRows(rows: FieldMappingDraftRow[]) {
  const systemRows = rows.filter((row) => Boolean(SYSTEM_FIELD_META[row.source]));
  const contentRows = rows.filter((row) => !SYSTEM_FIELD_META[row.source]);
  return [
    { kind: 'system', label: '系统溯源字段', rows: systemRows },
    { kind: 'content', label: '业务内容字段', rows: contentRows },
  ].filter((group) => group.rows.length > 0);
}

function summarizeMappingRows(rows: FieldMappingDraftRow[]) {
  const enabledRows = rows.filter((row) => row.included);
  return {
    enabled: enabledRows.length,
    system: enabledRows.filter((row) => Boolean(SYSTEM_FIELD_META[row.source])).length,
    content: enabledRows.filter((row) => !SYSTEM_FIELD_META[row.source]).length,
  };
}

function buildFieldMapping(rows: FieldMappingDraftRow[]): ExportFieldMapping {
  return {
    columns: rows
      .filter((row) => row.source.trim() && row.columnName.trim())
      .map((row) => ({
        source: row.source.trim(),
        columnName: row.columnName.trim(),
        included: row.included,
      })),
  };
}

function buildTrainingProfile(format: TrainingExportFormat, draft: TrainingProfileDraft): TrainingExportProfile | undefined {
  if (format === 'flat_table') {
    return undefined;
  }
  if (format === 'trl_dpo_jsonl') {
    return {
      promptSource: draft.promptSource.trim(),
      preferenceSource: draft.preferenceSource.trim(),
      choiceSources: {
        A: draft.choiceASource.trim(),
        B: draft.choiceBSource.trim(),
      },
    };
  }
  return {
    promptSource: draft.promptSource.trim(),
    completionSource: draft.completionSource.trim(),
  };
}

function parsePositiveInt(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}

function hasTrainingPackage(snapshot: ExportSnapshot) {
  return snapshot.fileManifest.some((file) => isTrainingJsonlFile(file.name) && (file.lines ?? 0) > 0);
}

function isTrainingJsonlFile(fileName?: string | null) {
  return fileName === 'openai-chat-sft.jsonl'
    || fileName === 'trl-sft.jsonl'
    || fileName === 'trl-dpo.jsonl';
}
