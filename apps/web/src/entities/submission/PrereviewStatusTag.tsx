import { Tag } from '@douyinfe/semi-ui';
import type { components } from '../../shared/api/generated/schema';

type PrereviewStatus = components['schemas']['PrereviewStatus'];

type PrereviewStatusTagProps = {
  status?: PrereviewStatus | null;
};

const STATUS_META: Record<PrereviewStatus, { label: string; tone: 'neutral' | 'info' | 'success' | 'danger' }> = {
  pending: { label: '待预审', tone: 'neutral' },
  processing: { label: '预审中', tone: 'info' },
  completed: { label: '预审完成', tone: 'success' },
  failed: { label: '预审失败', tone: 'danger' },
};

export function PrereviewStatusTag({ status }: PrereviewStatusTagProps) {
  const meta = STATUS_META[status ?? 'pending'];
  return <Tag className={`semantic-tag semantic-tag--${meta.tone}`}>{meta.label}</Tag>;
}
