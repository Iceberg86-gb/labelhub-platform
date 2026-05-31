import { Button, Input, Select, Space, Switch, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconSave, IconTickCircle } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { IconAiAssist, IconReviewFlow, IconStatusFlow } from '../../shared/ui/LabelHubIcons';
import { RoleBadge } from '../../shared/ui/RoleBadge';

type ProviderId = 'mock' | 'openai-compatible' | 'deepseek' | 'custom';
type ConnectionStatus = 'idle' | 'success' | 'error' | 'saved';

type LlmConfigState = {
  apiKey: string;
  baseUrl: string;
  enableAiReview: boolean;
  enableDesignerField: boolean;
  enableFieldAssist: boolean;
  estimatedCostPerCall: string;
  maxAttempts: string;
  modelName: string;
  provider: ProviderId;
  providerName: string;
  systemPrompt: string;
  timeoutSeconds: string;
};

const providerOptions = [
  { label: 'Mock Provider', value: 'mock' },
  { label: 'OpenAI-compatible', value: 'openai-compatible' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: '自定义 Provider', value: 'custom' },
];

const modelOptionsByProvider: Record<ProviderId, Array<{ label: string; value: string }>> = {
  mock: [{ label: 'mock-v1', value: 'mock-v1' }],
  'openai-compatible': [
    { label: 'gpt-4.1-mini', value: 'gpt-4.1-mini' },
    { label: 'gpt-4.1', value: 'gpt-4.1' },
    { label: 'deepseek-v4-flash', value: 'deepseek-v4-flash' },
  ],
  deepseek: [
    { label: 'deepseek-v4-flash', value: 'deepseek-v4-flash' },
    { label: 'deepseek-v4-pro', value: 'deepseek-v4-pro' },
  ],
  custom: [
    { label: '自定义模型', value: 'custom-model' },
  ],
};

const providerDefaults: Record<ProviderId, Pick<LlmConfigState, 'baseUrl' | 'modelName' | 'providerName'>> = {
  mock: {
    baseUrl: '',
    modelName: 'mock-v1',
    providerName: 'mock',
  },
  'openai-compatible': {
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-4.1-mini',
    providerName: 'openai-compatible',
  },
  deepseek: {
    baseUrl: 'https://api.deepseek.com/v1',
    modelName: 'deepseek-v4-flash',
    providerName: 'deepseek',
  },
  custom: {
    baseUrl: '',
    modelName: 'custom-model',
    providerName: 'custom',
  },
};

const initialConfig: LlmConfigState = {
  ...providerDefaults['openai-compatible'],
  apiKey: '',
  enableAiReview: true,
  enableDesignerField: true,
  enableFieldAssist: false,
  estimatedCostPerCall: '0.001',
  maxAttempts: '3',
  provider: 'openai-compatible',
  systemPrompt: '你是 LabelHub 的标注辅助模型。只给出可审计的参考建议,不要替代人工最终裁决。',
  timeoutSeconds: '30',
};

function maskApiKey(apiKey: string) {
  const trimmed = apiKey.trim();
  if (!trimmed) return '未添加';
  return `已添加 · 尾号 ${trimmed.slice(-4).padStart(4, '•')}`;
}

function statusLabel(status: ConnectionStatus) {
  if (status === 'success') return '连接可用';
  if (status === 'saved') return '配置已保存';
  if (status === 'error') return '等待补全密钥';
  return '待测试';
}

export function OwnerLlmSettingsPage() {
  const [config, setConfig] = useState<LlmConfigState>(initialConfig);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const modelOptions = modelOptionsByProvider[config.provider];
  const apiKeyLabel = useMemo(() => maskApiKey(config.apiKey), [config.apiKey]);

  const updateConfig = <Key extends keyof LlmConfigState>(key: Key, value: LlmConfigState[Key]) => {
    setConfig((current) => ({ ...current, [key]: value }));
    setConnectionStatus('idle');
  };

  const handleProviderChange = (value: unknown) => {
    const provider = (typeof value === 'string' ? value : 'openai-compatible') as ProviderId;
    setConfig((current) => ({
      ...current,
      ...providerDefaults[provider],
      provider,
    }));
    setConnectionStatus('idle');
  };

  const testConnection = () => {
    if (config.provider !== 'mock' && !config.apiKey.trim()) {
      setConnectionStatus('error');
      Toast.error('请先添加 API Key');
      return;
    }

    setConnectionStatus('success');
    Toast.success('连接测试通过');
  };

  const saveConfig = () => {
    if (config.provider !== 'mock' && !config.apiKey.trim()) {
      setConnectionStatus('error');
      Toast.error('请先添加 API Key');
      return;
    }

    setConnectionStatus('saved');
    Toast.success('LLM 接入配置已保存');
  };

  return (
    <section className="llm-settings-page" aria-label="Owner LLM settings">
      <header className="owner-page-hero llm-settings-hero">
        <div className="owner-page-hero__copy">
          <div className="owner-page-hero__meta">
            <RoleBadge role="OWNER" />
            <Typography.Text>模型接入与辅助范围</Typography.Text>
          </div>
          <Typography.Title heading={3} className="page-title">
            LLM 接入
          </Typography.Title>
          <Typography.Text type="tertiary">
            管理 API Key、Provider、模型与 LabelHub 内部 AI 辅助能力的启用范围。
          </Typography.Text>
        </div>
        <Space>
          <Button icon={<IconRefresh />} onClick={testConnection}>
            测试连接
          </Button>
          <Button icon={<IconSave />} theme="solid" type="primary" onClick={saveConfig}>
            保存配置
          </Button>
        </Space>
      </header>

      <section className="llm-status-grid" aria-label="LLM 接入状态">
        <div className="llm-status-card llm-status-card--primary">
          <span className="llm-status-card__icon"><IconAiAssist /></span>
          <span>当前 Provider</span>
          <strong>{config.providerName}</strong>
          <small>{config.modelName}</small>
        </div>
        <div className="llm-status-card">
          <span className="llm-status-card__icon"><IconTickCircle /></span>
          <span>连接状态</span>
          <strong>{statusLabel(connectionStatus)}</strong>
          <small>{apiKeyLabel}</small>
        </div>
        <div className="llm-status-card">
          <span className="llm-status-card__icon"><IconReviewFlow /></span>
          <span>AI 预审</span>
          <strong>{config.enableAiReview ? '已启用' : '未启用'}</strong>
          <small>仅生成辅助证据</small>
        </div>
        <div className="llm-status-card">
          <span className="llm-status-card__icon"><IconStatusFlow /></span>
          <span>人工裁决</span>
          <strong>保持最终权责</strong>
          <small>不允许模型自动终审</small>
        </div>
      </section>

      <div className="llm-settings-grid">
        <section className="llm-config-panel" aria-label="Provider 配置">
          <div className="llm-panel-head">
            <div>
              <Typography.Title heading={5}>Provider 配置</Typography.Title>
              <Typography.Text type="tertiary">用于 AI 预审、Designer LLM 字段与字段级建议。</Typography.Text>
            </div>
            <span className={`llm-connection-pill llm-connection-pill--${connectionStatus}`}>
              {statusLabel(connectionStatus)}
            </span>
          </div>

          <div className="llm-form-grid">
            <label className="llm-field">
              <Typography.Text strong>Provider</Typography.Text>
              <Select
                value={config.provider}
                optionList={providerOptions}
                onChange={handleProviderChange}
              />
            </label>

            <label className="llm-field">
              <Typography.Text strong>Provider 名称</Typography.Text>
              <Input
                value={config.providerName}
                placeholder="例如 openai-compatible / deepseek"
                onChange={(providerName) => updateConfig('providerName', providerName)}
              />
            </label>

            <label className="llm-field llm-field--wide">
              <Typography.Text strong>Base URL</Typography.Text>
              <Input
                value={config.baseUrl}
                placeholder="https://api.example.com/v1"
                onChange={(baseUrl) => updateConfig('baseUrl', baseUrl)}
              />
            </label>

            <label className="llm-field llm-field--wide">
              <Typography.Text strong>API Key</Typography.Text>
              <Input
                mode="password"
                value={config.apiKey}
                placeholder="粘贴 Provider API Key"
                onChange={(apiKey) => updateConfig('apiKey', apiKey)}
              />
              <Typography.Text className="llm-field__hint" type="tertiary">
                保存后界面只展示密钥尾号,避免在日常操作中回显完整 Key。
              </Typography.Text>
            </label>

            <label className="llm-field">
              <Typography.Text strong>模型预设</Typography.Text>
              <Select
                value={config.modelName}
                optionList={modelOptions}
                onChange={(modelName) => updateConfig('modelName', String(modelName))}
              />
            </label>

            <label className="llm-field">
              <Typography.Text strong>模型名称</Typography.Text>
              <Input
                value={config.modelName}
                placeholder="例如 deepseek-v4-flash"
                onChange={(modelName) => updateConfig('modelName', modelName)}
              />
            </label>

            <label className="llm-field llm-field--wide">
              <Typography.Text strong>默认系统 Prompt</Typography.Text>
              <TextArea
                autosize={{ minRows: 4, maxRows: 8 }}
                value={config.systemPrompt}
                onChange={(systemPrompt) => updateConfig('systemPrompt', systemPrompt)}
              />
            </label>
          </div>

          <div className="llm-advanced-grid" aria-label="高级调用参数">
            <label className="llm-field">
              <Typography.Text strong>Timeout</Typography.Text>
              <Input
                addonAfter="s"
                inputMode="numeric"
                value={config.timeoutSeconds}
                onChange={(timeoutSeconds) => updateConfig('timeoutSeconds', timeoutSeconds)}
              />
            </label>
            <label className="llm-field">
              <Typography.Text strong>重试次数</Typography.Text>
              <Input
                inputMode="numeric"
                value={config.maxAttempts}
                onChange={(maxAttempts) => updateConfig('maxAttempts', maxAttempts)}
              />
            </label>
            <label className="llm-field">
              <Typography.Text strong>估算单次成本</Typography.Text>
              <Input
                addonBefore="$"
                inputMode="decimal"
                value={config.estimatedCostPerCall}
                onChange={(estimatedCostPerCall) => updateConfig('estimatedCostPerCall', estimatedCostPerCall)}
              />
            </label>
          </div>
        </section>

        <aside className="llm-scope-panel" aria-label="使用范围">
          <div className="llm-panel-head">
            <div>
              <Typography.Title heading={5}>使用范围</Typography.Title>
              <Typography.Text type="tertiary">控制模型在哪些工作流里作为辅助能力出现。</Typography.Text>
            </div>
          </div>

          <div className="llm-scope-list">
            <label className="llm-scope-item">
              <span>
                <strong>AI 预审辅助</strong>
                <small>生成推荐结论、维度评分和字段级发现</small>
              </span>
              <Switch checked={config.enableAiReview} onChange={(enableAiReview) => updateConfig('enableAiReview', enableAiReview)} />
            </label>
            <label className="llm-scope-item">
              <span>
                <strong>Designer LLM 字段</strong>
                <small>支持任务负责人在表单里加入 LLM 交互字段</small>
              </span>
              <Switch checked={config.enableDesignerField} onChange={(enableDesignerField) => updateConfig('enableDesignerField', enableDesignerField)} />
            </label>
            <label className="llm-scope-item">
              <span>
                <strong>字段级建议</strong>
                <small>给标注员提供草稿建议,结果仍需人工确认</small>
              </span>
              <Switch checked={config.enableFieldAssist} onChange={(enableFieldAssist) => updateConfig('enableFieldAssist', enableFieldAssist)} />
            </label>
            <div className="llm-scope-item llm-scope-item--locked" role="note">
              <span>
                <strong>自动终审</strong>
                <small>已锁定关闭,人工审核保留最终裁决权</small>
              </span>
              <Switch checked={false} disabled />
            </div>
          </div>

          <div className="llm-safety-note">
            <Typography.Text strong>辅助原则</Typography.Text>
            <Typography.Text type="tertiary">
              LLM 接入只改变证据生成能力,不改变审核台的人工通过、打回与复核职责。
            </Typography.Text>
          </div>
        </aside>
      </div>
    </section>
  );
}
