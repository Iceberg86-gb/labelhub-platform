import { Button, Space, Tag, Tooltip } from '@douyinfe/semi-ui';
import type { OfflineDraftBufferStatus } from './useOfflineDraftBuffer';
import type { OfflineDraftSyncStatus } from './useOfflineDraftSync';
import type { UseAutosaveResult } from './useAutosave';

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export function AutosaveStatusTag({
  autosave,
  offlineDraft,
  offlineSync,
  onRetryOfflineDraftSync,
}: {
  autosave: UseAutosaveResult;
  offlineDraft?: OfflineDraftBufferStatus;
  offlineSync?: OfflineDraftSyncStatus;
  onRetryOfflineDraftSync?: () => void;
}) {
  if (offlineSync?.kind === 'syncing') {
    return (
      <Tooltip content="正在把本地草稿同步到服务器">
        <Tag className="autosave-status-tag" color="blue">
          本地草稿同步中
        </Tag>
      </Tooltip>
    );
  }

  if (offlineSync?.kind === 'retry-scheduled') {
    return (
      <Tooltip content="服务器暂时不可用,本地草稿稍后会自动重试同步。">
        <Space spacing={4}>
          <Tag className="autosave-status-tag" color="orange">
            本地草稿待同步
          </Tag>
          {onRetryOfflineDraftSync ? (
            <Button size="small" theme="borderless" onClick={onRetryOfflineDraftSync}>
              重试
            </Button>
          ) : null}
        </Space>
      </Tooltip>
    );
  }

  if (offlineSync?.kind === 'blocked') {
    return (
      <Tooltip content={offlineSync.reason === 'auth' ? '权限或登录状态已失效,本地草稿已保留。' : '本地草稿暂时无法同步。'}>
        <Space spacing={4}>
          <Tag className="autosave-status-tag" color="red">
            本地草稿无法同步
          </Tag>
          {onRetryOfflineDraftSync ? (
            <Button size="small" theme="borderless" onClick={onRetryOfflineDraftSync}>
              重试
            </Button>
          ) : null}
        </Space>
      </Tooltip>
    );
  }

  if (offlineSync?.kind === 'terminal-cleared') {
    return (
      <Tooltip content="此会话已在别处提交/释放,本地草稿已弃。">
        <Tag className="autosave-status-tag" color="red">
          本地草稿已弃
        </Tag>
      </Tooltip>
    );
  }

  if (offlineDraft?.kind === 'local-buffered') {
    return (
      <Tooltip content="服务器草稿保存失败,当前答案已暂存在本机,恢复网络后会同步。">
        <Tag className="autosave-status-tag" color="orange">
          本地已暂存
        </Tag>
      </Tooltip>
    );
  }

  if (offlineDraft?.kind === 'local-restored') {
    return (
      <Tooltip content="已恢复本机未同步草稿,下一次自动保存会尝试同步到服务器。">
        <Tag className="autosave-status-tag" color="orange">
          已恢复本地草稿
        </Tag>
      </Tooltip>
    );
  }

  if (offlineDraft?.kind === 'blocked') {
    return (
      <Tooltip content="本地草稿与当前会话的 Schema 版本不匹配,未自动恢复。">
        <Tag className="autosave-status-tag" color="red">
          本地草稿未恢复
        </Tag>
      </Tooltip>
    );
  }

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
