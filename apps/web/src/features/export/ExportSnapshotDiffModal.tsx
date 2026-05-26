import { Banner, Empty, Modal, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import type { ExportSnapshotDiff } from '../../entities/export/exportTypes';
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

  return (
    <Modal
      title={`快照对比: #${baseSnapshotId} ↔ #${compareSnapshotId}`}
      visible
      onCancel={onClose}
      footer={null}
      width={720}
    >
      <div className="export-diff-modal">
        {diffQuery.isLoading ? <Spin /> : null}
        {diffQuery.isError ? <Empty title="对比加载失败" description="请稍后重试。" /> : null}
        {diff ? (
          <>
            <Banner
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
              <Typography.Text strong>文件级 SHA-256 对照({fileLevelMatches.length} 个文件)</Typography.Text>
              <Table
                columns={[
                  { title: '文件', dataIndex: 'fileName', width: 210 },
                  { title: 'Base SHA-256', dataIndex: 'baseSha256', render: (value: string) => <HashText value={value} /> },
                  { title: 'Compare SHA-256', dataIndex: 'compareSha256', render: (value: string) => <HashText value={value} /> },
                  {
                    title: '匹配',
                    dataIndex: 'match',
                    width: 100,
                    render: (value: boolean) => <Tag color={value ? 'green' : 'red'}>{value ? '✓ 一致' : '✗ 不一致'}</Tag>,
                  },
                ]}
                dataSource={fileLevelMatches}
                rowKey="fileName"
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
      <Tag color={match ? 'green' : 'red'}>{match ? '✓ 一致' : '✗ 不一致'}</Tag>
    </div>
  );
}

function HashText({ value }: { value: FileMatch['baseSha256'] }) {
  return <Typography.Text className="mono-value">{shortHash(value)}</Typography.Text>;
}

function shortHash(value?: string | null) {
  return value ? `${value.slice(0, 16)}...` : '-';
}
