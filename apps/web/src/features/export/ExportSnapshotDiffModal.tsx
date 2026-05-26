import { Banner, Empty, Modal, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import type { ExportSnapshotDiff } from '../../entities/export/exportTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { useExportSnapshotDiffQuery } from './useExportSnapshotDiffQuery';

type ExportSnapshotDiffModalProps = {
  baseSnapshotId: number;
  compareSnapshotId: number;
  onClose: () => void;
};

type FileMatch = NonNullable<ExportSnapshotDiff['fileLevelMatches']>[number];

export function ExportSnapshotDiffModal({ baseSnapshotId, compareSnapshotId, onClose }: ExportSnapshotDiffModalProps) {
  const diffQuery = useExportSnapshotDiffQuery(baseSnapshotId, compareSnapshotId);
  const diff = diffQuery.data;
  const fileLevelMatches = diff?.fileLevelMatches ?? [];
  const matchingFileCount = fileLevelMatches.filter((file) => file.match).length;

  return (
    <Modal
      title={`快照对比: #${baseSnapshotId} ↔ #${compareSnapshotId}`}
      visible
      onCancel={onClose}
      footer={null}
      width={840}
    >
      <div className="export-diff-modal">
        {diffQuery.isLoading ? <Spin /> : null}
        {diffQuery.isError ? <Empty title="对比加载失败" description="请稍后重试。" /> : null}
        {diff ? (
          <>
            <Banner
              className={diff.equal ? 'export-diff-success-banner' : undefined}
              fullMode={false}
              type={diff.equal ? 'success' : 'warning'}
              closeIcon={null}
              title={diff.equal ? '两个快照内容完全一致' : '两个快照内容不一致'}
              description={
                diff.equal
                  ? 'manifestHash / sourceStateHash / fileHash 三层 hash 全部匹配,每个 file 的 SHA-256 也完全一致。这证明两次独立 export 产生了相同的可信训练数据集。'
                  : '部分 hash 不匹配,可能由于两次导出之间 source state 发生了变化,例如新增 ledger entry 或新 submission。'
              }
            />

            <section className="diff-hash-section">
              <Typography.Text strong>三层 Hash 对比</Typography.Text>
              <div className="hash-matches-grid">
                <HashMatchRow label="File Hash(所有文件聚合)" match={diff.hashMatches.fileHash} />
                <HashMatchRow label="Manifest Hash(content section)" match={diff.hashMatches.manifestHash} />
                <HashMatchRow label="Source State Hash(source facts 聚合)" match={diff.hashMatches.sourceStateHash} />
              </div>
            </section>

            <section className="diff-file-section">
              <div className="diff-file-section__heading">
                <Typography.Text strong>文件级 SHA-256 对照({fileLevelMatches.length} 个文件)</Typography.Text>
                <Tag className="diff-status-tag diff-status-tag--match">{matchingFileCount}/{fileLevelMatches.length} 一致</Tag>
              </div>
              <Table
                columns={[
                  { title: '文件', dataIndex: 'fileName', width: 210 },
                  { title: 'Base SHA-256', dataIndex: 'baseSha256', render: (value: string) => <HashText value={value} /> },
                  { title: 'Compare SHA-256', dataIndex: 'compareSha256', render: (value: string) => <HashText value={value} /> },
                  {
                    title: '匹配',
                    dataIndex: 'match',
                    width: 100,
                    render: (value: boolean) => <MatchTag match={value} />,
                  },
                ]}
                dataSource={fileLevelMatches}
                rowKey="fileName"
                onRow={(record) => ({ className: record?.match ? 'diff-file-row--match' : 'diff-file-row--mismatch' })}
                pagination={false}
                size="small"
              />
            </section>
          </>
        ) : null}
      </div>
    </Modal>
  );
}

function HashMatchRow({ label, match }: { label: string; match: boolean }) {
  return (
    <div className="hash-match-row">
      <Typography.Text type="tertiary">{label}</Typography.Text>
      <MatchTag match={match} />
    </div>
  );
}

function HashText({ value }: { value: FileMatch['baseSha256'] }) {
  return <TruncatedHash value={value} prefixLength={12} suffixLength={6} ariaLabel="File SHA-256 hash" />;
}

function MatchTag({ match }: { match: boolean }) {
  return (
    <Tag className={match ? 'diff-status-tag diff-status-tag--match' : 'diff-status-tag diff-status-tag--mismatch'}>
      {match ? '✓ 一致' : '✗ 不一致'}
    </Tag>
  );
}
