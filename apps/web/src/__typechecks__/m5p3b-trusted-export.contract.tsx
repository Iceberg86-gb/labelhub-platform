import { TrustedExportCard } from '../features/export/TrustedExportCard';
import { ExportSnapshotDiffModal } from '../features/export/ExportSnapshotDiffModal';
import { useCreateExportMutation } from '../features/export/useCreateExportMutation';
import { useExportSnapshotDiffQuery } from '../features/export/useExportSnapshotDiffQuery';
import { useTaskExportsQuery } from '../features/export/useTaskExportsQuery';

export function M5P3bTrustedExportContract() {
  useTaskExportsQuery(1, { page: 1, size: 20 });
  useCreateExportMutation();
  useExportSnapshotDiffQuery(1, 2);

  return (
    <>
      <TrustedExportCard taskId={1} />
      <ExportSnapshotDiffModal baseSnapshotId={1} compareSnapshotId={2} onClose={() => {}} />
    </>
  );
}
