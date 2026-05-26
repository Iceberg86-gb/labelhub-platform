import { Tag, Tooltip } from '@douyinfe/semi-ui';
import type { UseAutosaveResult } from './useAutosave';

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export function AutosaveStatusTag({ autosave }: { autosave: UseAutosaveResult }) {
  if (autosave.status === 'saving') {
    return (
      <Tooltip content="正在保存当前答案">
        <Tag className="autosave-status-tag" color="blue">
          保存中
        </Tag>
      </Tooltip>
    );
  }

  if (autosave.status === 'error') {
    return (
      <Tooltip content={autosave.lastError?.message ?? '保存失败,请检查网络后重试'}>
        <Tag className="autosave-status-tag" color="red">
          保存失败
        </Tag>
      </Tooltip>
    );
  }

  if (autosave.status === 'saved' && autosave.lastSavedAt) {
    return (
      <Tooltip content={`已保存于 ${timeFormatter.format(new Date(autosave.lastSavedAt))}`}>
        <Tag className="autosave-status-tag" color="green">
          已保存
        </Tag>
      </Tooltip>
    );
  }

  return (
    <Tooltip content="本次会话尚未保存">
      <Tag className="autosave-status-tag" color="grey">
        未保存
      </Tag>
    </Tooltip>
  );
}
