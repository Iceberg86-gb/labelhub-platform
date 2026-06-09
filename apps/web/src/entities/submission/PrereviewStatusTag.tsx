import { Tooltip } from '@douyinfe/semi-ui';
import type { components } from '../../shared/api/generated/schema';
import { StatusBadge, type BadgeTone } from '../../shared/ui';

type PrereviewStatus = components['schemas']['PrereviewStatus'];
type PrereviewSignals = components['schemas']['PrereviewSignals'];

type PrereviewStatusTagProps = {
  status?: PrereviewStatus | null;
  signals?: PrereviewSignals | null;
};

const STATUS_META: Record<PrereviewStatus, { label: string; tone: BadgeTone }> = {
  pending: { label: '待预审', tone: 'neutral' },
  processing: { label: '预审中', tone: 'info' },
  completed: { label: '预审完成', tone: 'success' },
  failed: { label: '预审失败', tone: 'danger' },
};

export function PrereviewStatusTag({ status, signals }: PrereviewStatusTagProps) {
  const meta = STATUS_META[status ?? 'pending'];
  const tag = <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>;

  if ((status ?? 'pending') !== 'failed' || !signals?.lastError) {
    return tag;
  }

  return (
    <Tooltip content={`失败原因: ${signals.lastError}`}>
      <span>{tag}</span>
    </Tooltip>
  );
}
