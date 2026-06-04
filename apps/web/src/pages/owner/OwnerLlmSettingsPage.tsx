import { Button, Input, Popconfirm, Select, Space, Switch, Tag, TextArea, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconEdit, IconPlus, IconRefresh, IconSave, IconTickCircle } from '@douyinfe/semi-icons';
import type { CSSProperties } from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  useActivateLlmProviderMutation,
  useDeleteLlmProviderMutation,
  useLlmProvidersQuery,
  useSaveLlmProviderMutation,
  useTestLlmProviderMutation,
  type LlmProviderConfig,
  type LlmProviderConfigRequest,
  type LlmProviderTestConnectionRequest,
} from '../../features/llm-provider/useLlmProviders';
import { IconReviewFlow, IconStatusFlow } from '../../shared/ui/LabelHubIcons';
import { RoleBadge } from '../../shared/ui/RoleBadge';
import { LlmProviderIcon, iconIdForProvider } from './llmProviderIcons';

type ProviderId = 'openai' | 'deepseek' | 'qwen' | 'doubao' | 'claude' | 'custom';
type ConnectionStatus = 'idle' | 'success' | 'error' | 'saved';
type FormMode = 'list' | 'new-preset' | 'new' | 'edit';

type LlmConfigState = {
  apiKey: string;
  baseUrl: string;
  enableAiReview: boolean;
  enableDesignerField: boolean;
  enableFieldAssist: boolean;
  maxAttempts: string;
  modelName: string;
  provider: ProviderId;
  providerName: string;
  secretRef: string;
  systemPrompt: string;
  timeoutSeconds: string;
};

type ProviderPreset = Pick<LlmConfigState, 'baseUrl' | 'modelName' | 'providerName'> & {
  hint?: string;
  icon: Parameters<typeof LlmProviderIcon>[0]['provider'];
  label: string;
  models: Array<{ label: string; value: string }>;
  value: ProviderId;
};

const providerPresets: Record<ProviderId, ProviderPreset> = {
  openai: {
    label: 'OpenAI',
    value: 'openai',
    icon: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-5.5',
    providerName: 'openai',
    models: [
      { label: 'gpt-5.5', value: 'gpt-5.5' },
      { label: 'gpt-5.4', value: 'gpt-5.4' },
      { label: 'gpt-5.4-mini', value: 'gpt-5.4-mini' },
    ],
  },
  deepseek: {
    label: 'DeepSeek',
    value: 'deepseek',
    icon: 'deepseek',
    baseUrl: 'https://api.deepseek.com/v1',
    modelName: 'deepseek-chat',
    providerName: 'deepseek',
    models: [{ label: 'deepseek-chat', value: 'deepseek-chat' }],
  },
  qwen: {
    label: 'Qwen(通义千问)',
    value: 'qwen',
    icon: 'qwen',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'qwen-plus',
    providerName: 'qwen',
    hint: '海外部署请改用 dashscope-intl 端点',
    models: [
      { label: 'qwen-plus', value: 'qwen-plus' },
      { label: 'qwen-max', value: 'qwen-max' },
      { label: 'qwen-turbo', value: 'qwen-turbo' },
    ],
  },
  doubao: {
    label: '豆包(火山方舟)',
    value: 'doubao',
    icon: 'doubao',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    modelName: 'doubao-seed-1-6-251015',
    providerName: 'doubao',
    hint: '可替换为你在方舟创建的 Model ID 或接入点 ID',
    models: [{ label: 'doubao-seed-1-6-251015', value: 'doubao-seed-1-6-251015' }],
  },
  claude: {
    label: 'Claude',
    value: 'claude',
    icon: 'anthropic',
    baseUrl: 'https://api.anthropic.com/v1',
    modelName: 'claude-opus-4-8',
    providerName: 'claude',
    hint: '经 Anthropic OpenAI 兼容层接入;接入后请用测试连接并真实运行一次 AI 预审验证 tool 输出',
    models: [
      { label: 'claude-opus-4-8', value: 'claude-opus-4-8' },
      { label: 'claude-sonnet-4-6', value: 'claude-sonnet-4-6' },
    ],
  },
  custom: {
    label: '自定义',
    value: 'custom',
    icon: 'custom',
    baseUrl: '',
    modelName: '',
    providerName: '',
    models: [],
  },
};

const providerPresetList = Object.values(providerPresets);

const providerOptions = providerPresetList.map((preset) => ({
  label: (
    <span className="llm-provider-option">
      <LlmProviderIcon provider={preset.icon} />
      <span>{preset.label}</span>
    </span>
  ),
  value: preset.value,
}));

function presetConfig(provider: ProviderId): Pick<LlmConfigState, 'baseUrl' | 'modelName' | 'providerName'> {
  const preset = providerPresets[provider];
  return {
    baseUrl: preset.baseUrl,
    modelName: preset.modelName,
    providerName: preset.providerName,
  };
}

const initialConfig: LlmConfigState = {
  ...presetConfig('openai'),
  apiKey: '',
  enableAiReview: true,
  enableDesignerField: true,
  enableFieldAssist: false,
  maxAttempts: '3',
  provider: 'openai',
  secretRef: '',
  systemPrompt: '你是 LabelHub 的标注辅助模型。只给出可审计的参考建议,不要替代人工最终裁决。',
  timeoutSeconds: '30',
};

function maskApiKey(apiKey: string) {
  const trimmed = apiKey.trim();
  if (!trimmed) return '未添加';
  return `待保存 · 尾号 ${trimmed.slice(-4).padStart(4, '•')}`;
}

function savedSecretLabel(provider?: LlmProviderConfig) {
  if (!provider?.hasSecret) return '未添加';
  return provider.secretLast4 ? `已保存 · 尾号 ${provider.secretLast4.padStart(4, '•')}` : '已保存 · 引用密钥';
}

function statusLabel(status: ConnectionStatus) {
  if (status === 'success') return '连接可用';
  if (status === 'saved') return '配置已保存';
  if (status === 'error') return '等待补全密钥';
  return '待测试';
}

function providerIdFrom(provider: LlmProviderConfig): ProviderId {
  const normalizedName = provider.providerName?.trim().toLowerCase();
  if (normalizedName && normalizedName in providerPresets) {
    return normalizedName as ProviderId;
  }
  return 'custom';
}

function labelForProvider(provider: LlmProviderConfig) {
  const providerId = providerIdFrom(provider);
  return providerId === 'custom' ? provider.providerName : providerPresets[providerId].label;
}

function stateFromProvider(provider: LlmProviderConfig, current: LlmConfigState): LlmConfigState {
  const providerId = providerIdFrom(provider);
  return {
    ...current,
    apiKey: '',
    baseUrl: provider.baseUrl ?? '',
    modelName: provider.modelName,
    provider: providerId,
    providerName: provider.providerName,
    secretRef: provider.secretRef ?? '',
  };
}

function requestFromConfig(config: LlmConfigState, enabled: boolean): LlmProviderConfigRequest {
  const secret = config.apiKey.trim();
  const secretRef = config.secretRef.trim();
  return {
    providerType: 'openai-compatible',
    providerName: config.providerName,
    baseUrl: config.baseUrl || undefined,
    modelName: config.modelName,
    secret: secret || undefined,
    secretRef: secretRef || undefined,
    enabled,
  };
}

function testRequestFromConfig(config: LlmConfigState): LlmProviderTestConnectionRequest {
  const secret = config.apiKey.trim();
  const secretRef = config.secretRef.trim();
  return {
    providerType: 'openai-compatible',
    providerName: config.providerName,
    baseUrl: config.baseUrl || undefined,
    modelName: config.modelName,
    secret: secret || undefined,
    secretRef: secretRef || undefined,
    timeoutSeconds: Number(config.timeoutSeconds) || 10,
  };
}

function modelOptionsFor(provider: ProviderId, modelName: string) {
  const presetModels = providerPresets[provider].models;
  if (presetModels.length > 0) return presetModels;
  return modelName ? [{ label: modelName, value: modelName }] : [];
}

export function OwnerLlmSettingsPage() {
  const [config, setConfig] = useState<LlmConfigState>(initialConfig);
  const [selectedProviderId, setSelectedProviderId] = useState<number | null>(null);
  const [formMode, setFormMode] = useState<FormMode>('list');
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const providersQuery = useLlmProvidersQuery();
  const saveProviderMutation = useSaveLlmProviderMutation();
  const deleteProviderMutation = useDeleteLlmProviderMutation();
  const testProviderMutation = useTestLlmProviderMutation();
  const activateProviderMutation = useActivateLlmProviderMutation();
  const providers = providersQuery.data ?? [];
  const currentProvider = useMemo(
    () => providers.find((provider) => provider.enabled) ?? providers[0],
    [providers],
  );
  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.id === selectedProviderId),
    [providers, selectedProviderId],
  );
  const modelOptions = modelOptionsFor(config.provider, config.modelName);
  const apiKeyLabel = useMemo(
    () => (config.apiKey.trim() ? maskApiKey(config.apiKey) : savedSecretLabel(selectedProvider ?? currentProvider)),
    [config.apiKey, currentProvider, selectedProvider],
  );
  const currentProviderName = currentProvider?.providerName ?? config.providerName;
  const currentModelName = currentProvider?.modelName ?? config.modelName;
  const currentIconId = iconIdForProvider(currentProviderName);

  useEffect(() => {
    if (selectedProviderId != null || !currentProvider || formMode !== 'list') {
      return;
    }
    setConfig((current) => stateFromProvider(currentProvider, current));
  }, [currentProvider, formMode, selectedProviderId]);

  const updateConfig = <Key extends keyof LlmConfigState>(key: Key, value: LlmConfigState[Key]) => {
    setConfig((current) => ({ ...current, [key]: value }));
    setConnectionStatus('idle');
  };

  const handleProviderChange = (value: unknown) => {
    const provider = (typeof value === 'string' && value in providerPresets ? value : 'openai') as ProviderId;
    setConfig((current) => ({
      ...current,
      ...presetConfig(provider),
      provider,
    }));
    setConnectionStatus('idle');
  };

  const openProviderList = () => {
    setFormMode('list');
    setSelectedProviderId(null);
    setConnectionStatus('idle');
  };

  const startCreateProvider = () => {
    setFormMode('new-preset');
    setSelectedProviderId(null);
    setConfig(initialConfig);
    setConnectionStatus('idle');
  };

  const selectPreset = (provider: ProviderId) => {
    setConfig({
      ...initialConfig,
      ...presetConfig(provider),
      provider,
    });
    setSelectedProviderId(null);
    setFormMode('new');
    setConnectionStatus('idle');
  };

  const editProvider = (provider: LlmProviderConfig) => {
    setSelectedProviderId(provider.id);
    setConfig((current) => stateFromProvider(provider, current));
    setFormMode('edit');
    setConnectionStatus('idle');
  };

  const hasSecretSource = selectedProvider?.hasSecret || Boolean(config.apiKey.trim()) || Boolean(config.secretRef.trim());

  const testConnection = async () => {
    if (!hasSecretSource) {
      setConnectionStatus('error');
      Toast.error('请先添加 API Key 或 Secret Ref');
      return;
    }

    try {
      const result = await testProviderMutation.mutateAsync({
        id: config.apiKey.trim() ? undefined : selectedProviderId ?? undefined,
        body: testRequestFromConfig(config),
      });
      setConnectionStatus(result.ok ? 'success' : 'error');
      if (result.ok) {
        Toast.success('连接测试通过');
      } else {
        Toast.error(result.message ?? '连接测试未通过');
      }
    } catch (error) {
      setConnectionStatus('error');
      Toast.error(error instanceof Error ? error.message : '连接测试失败');
    }
  };

  const saveConfig = async () => {
    if (!hasSecretSource) {
      setConnectionStatus('error');
      Toast.error('请先添加 API Key 或 Secret Ref');
      return;
    }

    try {
      const enabled = selectedProvider?.enabled ?? providers.length === 0;
      const saved = await saveProviderMutation.mutateAsync({
        id: selectedProviderId ?? undefined,
        body: requestFromConfig(config, enabled),
      });
      setSelectedProviderId(saved.id);
      setConfig((current) => ({ ...stateFromProvider(saved, current), apiKey: '' }));
      setFormMode('edit');
      setConnectionStatus('saved');
      Toast.success('LLM 接入配置已保存');
    } catch (error) {
      setConnectionStatus('error');
      Toast.error(error instanceof Error ? error.message : 'LLM 接入配置保存失败');
    }
  };

  const deleteProvider = async (provider: LlmProviderConfig) => {
    if (provider.enabled) {
      Toast.error('请先切换到其他 Provider');
      return;
    }
    try {
      await deleteProviderMutation.mutateAsync(provider.id);
      if (selectedProviderId === provider.id) {
        openProviderList();
      }
      Toast.success('LLM Provider 已删除');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : 'LLM Provider 删除失败');
    }
  };

  const deleteConfig = async () => {
    if (!selectedProvider) {
      return;
    }
    await deleteProvider(selectedProvider);
  };

  const activateProvider = async (provider: LlmProviderConfig) => {
    if (!provider.hasSecret) {
      Toast.error('需先配置密钥');
      return;
    }
    try {
      await activateProviderMutation.mutateAsync(provider.id);
      Toast.success('当前 Provider 已切换');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : 'LLM Provider 切换失败');
    }
  };

  const renderActivateButton = (provider: LlmProviderConfig) => {
    const button = (
      <Button
        disabled={!provider.hasSecret || activateProviderMutation.isPending}
        loading={activateProviderMutation.isPending}
      >
        设为当前
      </Button>
    );
    if (!provider.hasSecret) {
      return <Tooltip content="需先配置密钥">{button}</Tooltip>;
    }
    return (
      <Popconfirm
        title="切换当前 Provider"
        content="AI 预审将立即切换到此 Provider"
        okText="确认切换"
        cancelText="取消"
        position="topRight"
        autoAdjustOverflow
        getPopupContainer={() => document.body}
        onConfirm={() => activateProvider(provider)}
      >
        {button}
      </Popconfirm>
    );
  };

  const renderDeleteButton = (provider: LlmProviderConfig) => {
    const button = (
      <Button
        className="llm-delete-button"
        disabled={provider.enabled || deleteProviderMutation.isPending}
        icon={<IconDelete />}
        loading={deleteProviderMutation.isPending}
      >
        删除
      </Button>
    );
    if (provider.enabled) {
      return <Tooltip content="请先切换到其他 Provider">{button}</Tooltip>;
    }
    return (
      <Popconfirm
        title="删除 Provider"
        content={`确认删除 ${provider.providerName}？`}
        okText="删除"
        cancelText="取消"
        position="topRight"
        autoAdjustOverflow
        getPopupContainer={() => document.body}
        onConfirm={() => deleteProvider(provider)}
      >
        {button}
      </Popconfirm>
    );
  };

  const renderProviderList = () => (
    <>
      <div className="llm-panel-head">
        <div>
          <Typography.Title heading={5}>Provider 列表</Typography.Title>
          <Typography.Text type="tertiary">平台可保存多个 Provider,当前只启用其中一个供 AI 预审使用。</Typography.Text>
        </div>
        <Button icon={<IconPlus />} theme="solid" type="primary" onClick={startCreateProvider}>
          新建 Provider
        </Button>
      </div>

      <div style={providerListStyle} aria-label="Provider 卡片列表">
        {providers.length === 0 ? (
          <div style={emptyProviderStyle}>
            <Typography.Text strong>还没有 Provider</Typography.Text>
            <Typography.Text type="tertiary">先新建一个 Provider,保存密钥后即可设为当前使用者。</Typography.Text>
          </div>
        ) : providers.map((provider) => (
          <article key={provider.id} style={providerCardStyle} aria-label={`Provider ${provider.providerName}`}>
            <div style={providerCardHeaderStyle}>
              <span style={iconFrameStyle}>
                <LlmProviderIcon provider={iconIdForProvider(provider.providerName)} size={36} />
              </span>
              <div style={providerTitleStyle}>
                <strong>{labelForProvider(provider)}</strong>
                <small>{provider.providerName}</small>
              </div>
              {provider.enabled ? <Tag color="green">使用中</Tag> : null}
            </div>
            <dl style={providerMetaStyle}>
              <div style={providerMetaItemStyle}>
                <dt style={providerMetaTermStyle}>Base URL</dt>
                <dd style={providerMetaValueStyle}>{provider.baseUrl ?? '未配置'}</dd>
              </div>
              <div style={providerMetaItemStyle}>
                <dt style={providerMetaTermStyle}>模型</dt>
                <dd style={providerMetaValueStyle}>{provider.modelName}</dd>
              </div>
            </dl>
            <div style={providerActionsStyle}>
              {provider.enabled ? null : renderActivateButton(provider)}
              <Button icon={<IconEdit />} onClick={() => editProvider(provider)}>
                编辑
              </Button>
              {renderDeleteButton(provider)}
            </div>
          </article>
        ))}
      </div>
    </>
  );

  const renderPresetGrid = () => (
    <>
      <div className="llm-panel-head">
        <div>
          <Typography.Title heading={5}>选择厂商预置</Typography.Title>
          <Typography.Text type="tertiary">选择后会自动填入 Base URL、Provider 名称和模型预设,仍可在下一步手动修改。</Typography.Text>
        </div>
        <Button onClick={openProviderList}>返回列表</Button>
      </div>
      <div style={presetGridStyle} aria-label="厂商预置">
        {providerPresetList.map((preset) => (
          <button
            key={preset.value}
            style={presetCardStyle}
            type="button"
            onClick={() => selectPreset(preset.value)}
          >
            <span style={iconFrameStyle}>
              <LlmProviderIcon provider={preset.icon} size={36} />
            </span>
            <strong>{preset.label}</strong>
            <small>{preset.baseUrl || '手动填写接入地址'}</small>
          </button>
        ))}
      </div>
    </>
  );

  const renderConfigForm = () => {
    const preset = providerPresets[config.provider];
    const deleteButton = (
      <Button
        className="llm-delete-button"
        disabled={!selectedProvider || selectedProvider.enabled}
        loading={deleteProviderMutation.isPending}
        onClick={deleteConfig}
      >
        删除配置
      </Button>
    );

    return (
      <>
        <div className="llm-panel-head">
          <div>
            <Typography.Title heading={5}>{formMode === 'new' ? '新建 Provider' : '编辑 Provider'}</Typography.Title>
            <Typography.Text type="tertiary">保存后可在列表里设为当前 Provider;已保存密钥不会在界面回显。</Typography.Text>
          </div>
          <span className={`llm-connection-pill llm-connection-pill--${connectionStatus}`}>
            {statusLabel(connectionStatus)}
          </span>
        </div>

        <div className="llm-form-grid">
          <label className="llm-field">
            <Typography.Text strong>厂商预置</Typography.Text>
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
              placeholder="例如 openai / deepseek / qwen"
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
            {preset.hint ? (
              <Typography.Text className="llm-field__hint" type="tertiary">{preset.hint}</Typography.Text>
            ) : null}
          </label>

          <label className="llm-field llm-field--wide">
            <Typography.Text strong>API Key</Typography.Text>
            <Input
              mode="password"
              value={config.apiKey}
              placeholder={selectedProvider?.hasSecret ? '留空则沿用已保存密钥' : '粘贴 Provider API Key'}
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
              placeholder="例如 deepseek-chat"
              onChange={(modelName) => updateConfig('modelName', modelName)}
            />
          </label>

          <label className="llm-field llm-field--wide">
            <Typography.Text strong>Secret Ref</Typography.Text>
            <Input
              value={config.secretRef}
              placeholder="可选: Vault/KMS/环境变量别名"
              onChange={(secretRef) => updateConfig('secretRef', secretRef)}
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
        </div>

        <div className="llm-panel-actions">
          <Button icon={<IconRefresh />} loading={testProviderMutation.isPending} onClick={testConnection}>
            测试连接
          </Button>
          <Button icon={<IconSave />} loading={saveProviderMutation.isPending} theme="solid" type="primary" onClick={saveConfig}>
            保存配置
          </Button>
          {selectedProvider?.enabled ? <Tooltip content="请先切换到其他 Provider">{deleteButton}</Tooltip> : deleteButton}
          <Button onClick={openProviderList}>返回列表</Button>
          <Typography.Text type="tertiary">
            {providersQuery.isLoading ? '正在读取已保存 Provider...' : `${providers.length} 个平台 Provider 已接入`}
          </Typography.Text>
        </div>
      </>
    );
  };

  return (
    <section className="llm-settings-page" aria-label="Platform LLM settings">
      <header className="owner-page-hero llm-settings-hero">
        <div className="owner-page-hero__copy">
          <div className="owner-page-hero__meta">
            <RoleBadge role="PLATFORM_ADMIN" />
            <Typography.Text>模型接入与辅助范围</Typography.Text>
          </div>
          <Typography.Title heading={3} className="page-title">
            LLM 接入
          </Typography.Title>
          <Typography.Text type="tertiary">
            管理平台级 API Key、Provider、模型与 LabelHub 内部 AI 辅助能力的启用范围。
          </Typography.Text>
        </div>
        <Space>
          <Button icon={<IconRefresh />} loading={providersQuery.isLoading} onClick={() => providersQuery.refetch()}>
            刷新
          </Button>
          <Button icon={<IconPlus />} theme="solid" type="primary" onClick={startCreateProvider}>
            新建 Provider
          </Button>
        </Space>
      </header>

      <section className="llm-status-grid" aria-label="LLM 接入状态">
        <div className="llm-status-card llm-status-card--primary">
          <span className="llm-status-card__icon"><LlmProviderIcon provider={currentIconId} /></span>
          <span>当前 Provider</span>
          <strong>{currentProviderName || '未配置'}</strong>
          <small>平台级 · {currentModelName || '未选择模型'}</small>
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
          {formMode === 'list' ? renderProviderList() : null}
          {formMode === 'new-preset' ? renderPresetGrid() : null}
          {formMode === 'new' || formMode === 'edit' ? renderConfigForm() : null}
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
                <strong>Designer AI 交互字段</strong>
                <small>支持任务负责人在表单里加入 AI 交互字段</small>
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

const providerListStyle: CSSProperties = {
  display: 'grid',
  gap: 12,
};

const providerCardStyle: CSSProperties = {
  background: 'var(--color-surface-subtle)',
  border: '1px solid var(--color-border-subtle)',
  borderRadius: 8,
  display: 'grid',
  gap: 14,
  minWidth: 0,
  padding: 16,
};

const providerCardHeaderStyle: CSSProperties = {
  alignItems: 'center',
  display: 'grid',
  gap: 12,
  gridTemplateColumns: '44px minmax(0, 1fr) auto',
  minWidth: 0,
};

const iconFrameStyle: CSSProperties = {
  alignItems: 'center',
  background: 'var(--color-main-surface)',
  border: '1px solid var(--color-border-subtle)',
  borderRadius: 8,
  color: 'var(--color-accent-blue)',
  display: 'inline-flex',
  height: 40,
  justifyContent: 'center',
  width: 40,
};

const providerTitleStyle: CSSProperties = {
  display: 'grid',
  gap: 4,
  minWidth: 0,
};

const providerMetaStyle: CSSProperties = {
  display: 'grid',
  gap: 8,
  gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
  margin: 0,
};

const providerMetaItemStyle: CSSProperties = {
  display: 'grid',
  gap: 4,
  minWidth: 0,
};

const providerMetaTermStyle: CSSProperties = {
  color: 'var(--color-text-tertiary)',
  fontSize: 'var(--font-size-caption)',
  lineHeight: 'var(--line-height-caption)',
};

const providerMetaValueStyle: CSSProperties = {
  color: 'var(--color-text-primary)',
  fontSize: 'var(--font-size-small)',
  lineHeight: 'var(--line-height-small)',
  margin: 0,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

const providerActionsStyle: CSSProperties = {
  alignItems: 'center',
  display: 'flex',
  flexWrap: 'wrap',
  gap: 8,
};

const presetGridStyle: CSSProperties = {
  display: 'grid',
  gap: 12,
  gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
};

const presetCardStyle: CSSProperties = {
  alignItems: 'center',
  background: 'var(--color-surface-subtle)',
  border: '1px solid var(--color-border-subtle)',
  borderRadius: 8,
  color: 'var(--color-text-primary)',
  cursor: 'pointer',
  display: 'grid',
  gap: 10,
  justifyItems: 'center',
  minHeight: 132,
  minWidth: 0,
  padding: 14,
  textAlign: 'center',
};

const emptyProviderStyle: CSSProperties = {
  alignContent: 'center',
  background: 'var(--color-surface-subtle)',
  border: '1px dashed var(--color-border-subtle)',
  borderRadius: 8,
  display: 'grid',
  gap: 8,
  minHeight: 160,
  padding: 20,
  textAlign: 'center',
};
