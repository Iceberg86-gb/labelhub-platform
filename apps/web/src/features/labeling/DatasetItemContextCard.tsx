import { Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';

type DatasetItemContextCardProps = {
  itemPayload: unknown;
  sourceLabel?: 'claimSnapshot' | 'datasetItem' | 'none';
};

type DatasetItemPayloadSelection = {
  claimSnapshot?: unknown;
  datasetItemPayload?: unknown;
};

type SelectedDatasetItemPayload = {
  payload: unknown;
  source: 'claimSnapshot' | 'datasetItem' | 'none';
};

const KNOWN_KEYS = new Set([
  'prompt',
  'model_answer',
  'reference',
  'tags',
  'difficulty',
  'category',
  'media_url',
  'media_type',
  'content_markdown',
]);

export function selectDatasetItemPayload(input: DatasetItemPayloadSelection): SelectedDatasetItemPayload {
  const snapshotPayload = readObjectProperty(input.claimSnapshot, 'datasetItemPayload');
  if (snapshotPayload !== undefined && snapshotPayload !== null) {
    return { payload: snapshotPayload, source: 'claimSnapshot' };
  }

  if (input.datasetItemPayload !== undefined && input.datasetItemPayload !== null) {
    return { payload: input.datasetItemPayload, source: 'datasetItem' };
  }

  return { payload: null, source: 'none' };
}

export function DatasetItemContextCard({ itemPayload, sourceLabel = 'none' }: DatasetItemContextCardProps) {
  const objectPayload = isPlainObject(itemPayload) ? itemPayload : null;
  const knownSections = objectPayload ? renderKnownSections(objectPayload) : [];
  const rawEntries = objectPayload
    ? Object.entries(objectPayload).filter(([key]) => knownSections.length === 0 || !KNOWN_KEYS.has(key))
    : [];
  const shouldShowRaw = rawEntries.length > 0 || (!objectPayload && itemPayload !== null && itemPayload !== undefined);

  return (
    <Card className="dataset-item-context-card" bodyStyle={{ padding: 24 }}>
      <div className="dataset-item-context-card__header">
        <div>
          <Typography.Title heading={5} className="dataset-item-context-card__title">
            待评审内容
          </Typography.Title>
          <Typography.Text type="tertiary">请根据领取时冻结的样本内容完成下方评分。</Typography.Text>
        </div>
        {sourceLabel !== 'none' ? (
          <Tag color={sourceLabel === 'claimSnapshot' ? 'blue' : 'orange'}>
            {sourceLabel === 'claimSnapshot' ? '领取快照' : '实时数据 fallback'}
          </Tag>
        ) : null}
      </div>

      {knownSections.length > 0 ? <div className="dataset-item-context-card__known">{knownSections}</div> : null}

      {shouldShowRaw ? <RawPayload payload={objectPayload ? Object.fromEntries(rawEntries) : itemPayload} /> : null}

      {knownSections.length === 0 && !shouldShowRaw ? (
        <Typography.Text type="tertiary">暂无可展示的样本内容。</Typography.Text>
      ) : null}
    </Card>
  );
}

function renderKnownSections(payload: Record<string, unknown>) {
  const sections: ReactNode[] = [];
  const metadata: ReactNode[] = [];

  const prompt = asNonEmptyString(payload.prompt);
  if (prompt) {
    sections.push(<TextBlock key="prompt" label="问题" value={prompt} prominent />);
  }

  const modelAnswer = asNonEmptyString(payload.model_answer);
  if (modelAnswer) {
    sections.push(<TextBlock key="model_answer" label="模型回答" value={modelAnswer} />);
  }

  const reference = asNonEmptyString(payload.reference);
  if (reference) {
    sections.push(<TextBlock key="reference" label="参考答案" value={reference} />);
  }

  const tags = asStringList(payload.tags);
  if (tags.length > 0) {
    metadata.push(
      <Space key="tags" spacing={4} wrap>
        <Typography.Text type="tertiary">标签</Typography.Text>
        {tags.map((tag) => (
          <Tag key={tag} color="green">
            {tag}
          </Tag>
        ))}
      </Space>,
    );
  } else {
    const tagText = asNonEmptyString(payload.tags);
    if (tagText) metadata.push(<MetaItem key="tags" label="标签" value={tagText} />);
  }

  addMetadata(metadata, 'difficulty', '难度', payload.difficulty);
  addMetadata(metadata, 'category', '分类', payload.category);
  addMetadata(metadata, 'media_type', '媒体类型', payload.media_type);

  const mediaUrl = asNonEmptyString(payload.media_url);
  if (mediaUrl) {
    metadata.push(
      <span key="media_url" className="dataset-item-context-card__meta-item">
        <Typography.Text type="tertiary">媒体链接</Typography.Text>
        <a href={mediaUrl} target="_blank" rel="noreferrer">
          {mediaUrl}
        </a>
      </span>,
    );
  }

  if (metadata.length > 0) {
    sections.push(
      <div key="metadata" className="dataset-item-context-card__metadata">
        {metadata}
      </div>,
    );
  }

  const markdown = asNonEmptyString(payload.content_markdown);
  if (markdown) {
    sections.push(<TextBlock key="content_markdown" label="补充内容" value={markdown} preformatted />);
  }

  return sections;
}

function addMetadata(target: ReactNode[], key: string, label: string, value: unknown) {
  const text = asNonEmptyString(value);
  if (text) target.push(<MetaItem key={key} label={label} value={text} />);
}

function TextBlock({
  label,
  value,
  prominent = false,
  preformatted = false,
}: {
  label: string;
  value: string;
  prominent?: boolean;
  preformatted?: boolean;
}) {
  return (
    <div className={`dataset-item-context-card__block${prominent ? ' dataset-item-context-card__block--prominent' : ''}`}>
      <Typography.Text strong>{label}</Typography.Text>
      <div className={preformatted ? 'dataset-item-context-card__pre' : 'dataset-item-context-card__text'}>{value}</div>
    </div>
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <span className="dataset-item-context-card__meta-item">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <Typography.Text>{value}</Typography.Text>
    </span>
  );
}

function RawPayload({ payload }: { payload: unknown }) {
  if (isPlainObject(payload)) {
    const entries = Object.entries(payload);
    if (entries.length === 0) return null;

    return (
      <details className="dataset-item-context-card__raw" open>
        <summary>原始数据</summary>
        <div className="dataset-item-context-card__raw-grid">
          {entries.map(([key, value]) => (
            <div key={key} className="dataset-item-context-card__raw-row">
              <Typography.Text strong>{key}</Typography.Text>
              {isScalar(value) ? (
                <Typography.Text>{formatScalar(value)}</Typography.Text>
              ) : (
                <pre className="dataset-item-context-card__pre">{formatJson(value)}</pre>
              )}
            </div>
          ))}
        </div>
      </details>
    );
  }

  return (
    <details className="dataset-item-context-card__raw" open>
      <summary>原始数据</summary>
      <pre className="dataset-item-context-card__pre">{formatJson(payload)}</pre>
    </details>
  );
}

function readObjectProperty(value: unknown, key: string) {
  return isPlainObject(value) ? value[key] : undefined;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]';
}

function isScalar(value: unknown) {
  return value === null || ['string', 'number', 'boolean'].includes(typeof value);
}

function asNonEmptyString(value: unknown) {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function asStringList(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0).map((item) => item.trim());
}

function formatScalar(value: unknown) {
  if (value === null) return 'null';
  if (typeof value === 'string') return value;
  return String(value);
}

function formatJson(value: unknown) {
  try {
    return JSON.stringify(value, null, 2) ?? '';
  } catch {
    return String(value);
  }
}
