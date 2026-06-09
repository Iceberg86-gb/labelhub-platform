import { Button, Card, Empty, SideSheet, Spin, Typography } from '@douyinfe/semi-ui';
import { IconCalendarClock, IconHash, IconListView } from '@douyinfe/semi-icons';
import { StatusBadge } from '../../shared/ui';
import { useMemo, useState } from 'react';
import { previewJson } from '../../entities/schema/schemaPreview';
import { summarizeSchema } from '../../entities/schema/schemaSummary';
import type { SchemaVersion } from '../../entities/schema/schemaTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { useSchemaVersionsQuery } from './useSchemaVersionsQuery';

type VersionHistoryDrawerProps = {
  visible: boolean;
  schemaId: number;
  currentVersionId: number | null;
  onClose: () => void;
};

export function VersionHistoryDrawer({ visible, schemaId, currentVersionId, onClose }: VersionHistoryDrawerProps) {
  const versionsQuery = useSchemaVersionsQuery(schemaId);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const versions = useMemo(
    () => [...(versionsQuery.data ?? [])].sort((a, b) => b.versionNumber - a.versionNumber),
    [versionsQuery.data],
  );

  const toggleExpanded = (versionId: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(versionId)) {
        next.delete(versionId);
      } else {
        next.add(versionId);
      }
      return next;
    });
  };

  return (
    <SideSheet
      title="Schema 版本历史"
      visible={visible}
      width={640}
      maskStyle={{ backgroundColor: 'color-mix(in srgb, var(--color-primary-black) 34%, transparent)' }}
      onCancel={onClose}
    >
      {versionsQuery.isLoading ? (
        <div className="version-history-state">
          <Spin />
        </div>
      ) : versionsQuery.isError ? (
        <Empty
          title="版本历史加载失败"
          description={versionsQuery.error instanceof Error ? versionsQuery.error.message : '请稍后重试。'}
        />
      ) : versions.length === 0 ? (
        <Empty title="暂无版本" description="发布第一个版本后会出现在这里。" />
      ) : (
        <div className="version-history-list">
          {versions.map((version) => (
            <div className="version-history-timeline-item" key={version.id}>
              <span className="version-history-timeline-node" aria-hidden />
              <VersionCard
                version={version}
                expanded={expandedIds.has(version.id)}
                current={version.id === currentVersionId}
                onToggle={() => toggleExpanded(version.id)}
              />
            </div>
          ))}
        </div>
      )}
    </SideSheet>
  );
}

type VersionCardProps = {
  version: SchemaVersion;
  expanded: boolean;
  current: boolean;
  onToggle: () => void;
};

function VersionCard({ version, expanded, current, onToggle }: VersionCardProps) {
  const summary = summarizeSchema(version.schemaJson);

  return (
    <Card className={['version-history-card', current ? 'version-history-card--current' : ''].filter(Boolean).join(' ')}>
      <div className="version-history-card__header">
        <div>
          <Typography.Title heading={5}>v{version.versionNumber}</Typography.Title>
          <Typography.Text type="tertiary">{formatDateTime(version.publishedAt)}</Typography.Text>
        </div>
        {current ? <StatusBadge tone="accent">当前版本</StatusBadge> : null}
      </div>

      <div className="version-history-meta">
        <span>
          <IconHash aria-hidden />
          contentHash: <TruncatedHash value={version.contentHash} prefixLength={8} suffixLength={6} ariaLabel={`v${version.versionNumber} content hash`} />
        </span>
        <span>
          <IconListView aria-hidden />
          字段总数: {summary.totalCount}（顶层 {summary.topLevelCount} / 嵌套 {summary.nestedCount}）
        </span>
        <span>
          <IconCalendarClock aria-hidden />
          {formatDateTime(version.publishedAt)}
        </span>
      </div>

      <Button className="version-history-json-toggle" theme="borderless" type="tertiary" onClick={onToggle}>
        {expanded ? '收起 JSON' : '展开 JSON'}
      </Button>
      {expanded ? <pre className="schema-json-preview schema-version-json-preview">{previewJson(version.schemaJson)}</pre> : null}
    </Card>
  );
}

function formatDateTime(value?: string) {
  if (!value) {
    return '发布时间未知';
  }
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
