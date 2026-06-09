import type { ReactElement, ReactNode } from 'react';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LlmProviderConfig } from '../../features/llm-provider/useLlmProviders';

const providerMocks = vi.hoisted(() => ({
  activateMutation: {
    isPending: false,
    mutateAsync: vi.fn(),
  },
  deleteMutation: {
    isPending: false,
    mutateAsync: vi.fn(),
  },
  providersQuery: {
    data: [] as LlmProviderConfig[],
    isLoading: false,
    refetch: vi.fn(),
  },
  saveMutation: {
    isPending: false,
    mutateAsync: vi.fn(),
  },
  testMutation: {
    isPending: false,
    mutateAsync: vi.fn(),
  },
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

function textFrom(value: ReactNode): string {
  if (value == null || typeof value === 'boolean') return '';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.map(textFrom).join('');
  if (typeof value === 'object' && 'props' in value) {
    return textFrom((value as { props?: { children?: ReactNode } }).props?.children);
  }
  return '';
}

vi.mock('@douyinfe/semi-icons', () => ({
  IconDelete: () => <span />,
  IconEdit: () => <span />,
  IconPlus: () => <span />,
  IconRefresh: () => <span />,
  IconSave: () => <span />,
  IconTickCircle: () => <span />,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({
    children,
    className,
    disabled,
    icon,
    onClick,
  }: {
    children?: ReactNode;
    className?: string;
    disabled?: boolean;
    icon?: ReactNode;
    onClick?: () => void;
  }) => (
    <button className={className} disabled={disabled} type="button" onClick={onClick}>
      {icon}
      {children}
    </button>
  ),
  Input: ({
    addonAfter,
    addonBefore,
    mode,
    onChange,
    placeholder,
    value,
  }: {
    addonAfter?: ReactNode;
    addonBefore?: ReactNode;
    mode?: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    value?: ReactNode;
  }) => (
    <label>
      {addonBefore}
      <input
        placeholder={placeholder}
        type={mode === 'password' ? 'password' : 'text'}
        value={String(value ?? '')}
        onChange={(event) => onChange?.(event.currentTarget.value)}
      />
      {addonAfter}
    </label>
  ),
  Popconfirm: ({
    children,
    content,
    onConfirm,
    title,
  }: {
    children?: ReactNode;
    content?: ReactNode;
    onConfirm?: () => void;
    title?: ReactNode;
  }) => (
    <span data-popconfirm-content={textFrom(content)} data-popconfirm-title={textFrom(title)} onClick={onConfirm}>
      {children}
    </span>
  ),
  Select: ({
    optionList,
    value,
    onChange,
  }: {
    optionList?: Array<{ label: ReactNode; value: string }>;
    value?: string;
    onChange?: (value: string) => void;
  }) => (
    <select value={value} onChange={(event) => onChange?.(event.currentTarget.value)}>
      {optionList?.map((option) => (
        <option key={option.value} value={option.value}>{textFrom(option.label)}</option>
      ))}
    </select>
  ),
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Switch: ({ checked, disabled }: { checked?: boolean; disabled?: boolean }) => (
    <input checked={checked} disabled={disabled} readOnly type="checkbox" />
  ),
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  TextArea: ({ value, onChange }: { value?: ReactNode; onChange?: (value: string) => void }) => (
    <textarea value={String(value ?? '')} onChange={(event) => onChange?.(event.currentTarget.value)} />
  ),
  Toast: providerMocks.toast,
  Tooltip: ({ children, content }: { children?: ReactNode; content?: ReactNode }) => (
    <span data-tooltip-content={textFrom(content)}>{children}</span>
  ),
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

vi.mock('../../features/llm-provider/useLlmProviders', () => ({
  useActivateLlmProviderMutation: () => providerMocks.activateMutation,
  useDeleteLlmProviderMutation: () => providerMocks.deleteMutation,
  useLlmProvidersQuery: () => providerMocks.providersQuery,
  useSaveLlmProviderMutation: () => providerMocks.saveMutation,
  useTestLlmProviderMutation: () => providerMocks.testMutation,
}));

import { OwnerLlmSettingsPage } from './OwnerLlmSettingsPage';

const providerFixture = (overrides: Partial<LlmProviderConfig>): LlmProviderConfig => ({
  id: overrides.id ?? 1,
  scope: 'platform',
  providerType: overrides.providerType ?? 'openai-compatible',
  providerName: overrides.providerName ?? 'deepseek',
  baseUrl: overrides.baseUrl ?? 'https://api.deepseek.com/v1',
  modelName: overrides.modelName ?? 'deepseek-chat',
  enabled: overrides.enabled ?? false,
  hasSecret: overrides.hasSecret ?? true,
  secretLast4: overrides.secretLast4 ?? 'test',
  createdAt: '2026-06-04T00:00:00Z',
  updatedAt: '2026-06-04T00:00:00Z',
});

describe('OwnerLlmSettingsPage', () => {
  beforeEach(() => {
    providerMocks.providersQuery.data = [];
    providerMocks.providersQuery.isLoading = false;
    providerMocks.providersQuery.refetch.mockReset();
    providerMocks.activateMutation.mutateAsync.mockReset();
    providerMocks.deleteMutation.mutateAsync.mockReset();
    providerMocks.saveMutation.mutateAsync.mockReset();
    providerMocks.testMutation.mutateAsync.mockReset();
    providerMocks.toast.error.mockReset();
    providerMocks.toast.success.mockReset();
    providerMocks.saveMutation.mutateAsync.mockImplementation(async ({ body }) => ({
      id: 7,
      scope: 'platform',
      providerType: body.providerType,
      providerName: body.providerName,
      baseUrl: body.baseUrl,
      modelName: body.modelName,
      enabled: body.enabled,
      hasSecret: true,
      secretLast4: 'test',
      createdAt: '2026-06-04T00:00:00Z',
      updatedAt: '2026-06-04T00:00:00Z',
    }));
    providerMocks.activateMutation.mutateAsync.mockResolvedValue(providerFixture({ id: 3, providerName: 'qwen', enabled: true }));
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('does not show the new-provider preset as current provider when no provider is saved', () => {
    providerMocks.providersQuery.data = [];

    const html = renderToString(<OwnerLlmSettingsPage />);

    expect(html).toContain('当前 Provider');
    expect(html).toContain('未接入');
    expect(html).toContain('暂无启用 Provider');
    expect(html).toContain('还没有 Provider');
    expect(html).not.toContain('平台级 · gpt-5.5');
    expect(html).not.toContain('<strong>openai</strong>');
  });

  it('renders provider cards with current badge, brand icons, and guarded actions', () => {
    providerMocks.providersQuery.data = [
      providerFixture({ id: 2, providerName: 'deepseek', enabled: true, hasSecret: true }),
      providerFixture({ id: 3, providerName: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-plus', enabled: false, hasSecret: false }),
      providerFixture({ id: 4, providerName: 'unknown-vendor', baseUrl: 'https://unknown.example/v1', modelName: 'unknown-model', enabled: false, hasSecret: true }),
    ];

    const html = renderToString(<OwnerLlmSettingsPage />);

    expect(html).toContain('llm-settings-page');
    expect(html).toContain('Provider 列表');
    expect(html).toContain('DeepSeek');
    expect(html).toContain('使用中');
    expect(html).toContain('https://api.deepseek.com/v1');
    expect(html).toContain('qwen-plus');
    expect(html).toContain('unknown-vendor');
    expect(html).toContain('data-provider-icon="deepseek"');
    expect(html).toContain('data-provider-icon="qwen"');
    expect(html).toContain('data-provider-icon="custom"');
    expect(html).toContain('需先配置密钥');
    expect(html).toContain('请先切换到其他 Provider');
    expect(html).not.toContain('估算单次成本');
  });

  it('confirms activation through the generated mutation and surfaces backend errors', async () => {
    providerMocks.providersQuery.data = [
      providerFixture({ id: 2, providerName: 'deepseek', enabled: true }),
      providerFixture({ id: 3, providerName: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-plus', enabled: false, hasSecret: true }),
    ];

    const rendered = await renderClient(<OwnerLlmSettingsPage />);

    expect(rendered.html()).toContain('AI 预审将立即切换到此 Provider');
    await rendered.click(rendered.button('设为当前'));
    expect(providerMocks.activateMutation.mutateAsync).toHaveBeenCalledWith(3);
    expect(providerMocks.toast.success).toHaveBeenCalledWith('当前 Provider 已切换');

    providerMocks.activateMutation.mutateAsync.mockRejectedValueOnce(new Error('provider_secret_missing'));
    await rendered.click(rendered.button('设为当前'));
    expect(providerMocks.toast.error).toHaveBeenCalledWith('provider_secret_missing');

    rendered.unmount();
  });

  it('keeps no-secret providers from activation and enabled providers from deletion', async () => {
    providerMocks.providersQuery.data = [
      providerFixture({ id: 2, providerName: 'deepseek', enabled: true, hasSecret: true }),
      providerFixture({ id: 3, providerName: 'qwen', enabled: false, hasSecret: false }),
    ];

    const rendered = await renderClient(<OwnerLlmSettingsPage />);

    expect(rendered.disabledButton('设为当前')).toBeTruthy();
    expect(rendered.disabledButton('删除')).toBeTruthy();
    expect(rendered.html()).toContain('需先配置密钥');
    expect(rendered.html()).toContain('请先切换到其他 Provider');
    rendered.unmount();
  });

  it('fills each new-provider preset from the icon grid and saves as openai-compatible', async () => {
    providerMocks.providersQuery.data = [
      providerFixture({ id: 2, providerName: 'deepseek', enabled: true, hasSecret: true }),
    ];

    const cases = [
      { label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-5.5', providerName: 'openai' },
      { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat', providerName: 'deepseek' },
      { label: 'Qwen(通义千问)', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus', providerName: 'qwen' },
      { label: '豆包(火山方舟)', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', model: 'doubao-seed-1-6-251015', providerName: 'doubao' },
      { label: 'Claude', baseUrl: 'https://api.anthropic.com/v1', model: 'claude-opus-4-8', providerName: 'claude' },
    ];

    for (const preset of cases) {
      const rendered = await renderClient(<OwnerLlmSettingsPage />);
      await rendered.click(rendered.button('新建 Provider'));
      await rendered.click(rendered.button(preset.label));
      await rendered.changeInput(rendered.input('粘贴 Provider API Key'), 'sk-test-provider-key');

      expect(rendered.html()).toContain(preset.baseUrl);
      expect(rendered.html()).toContain(preset.model);
      expect(rendered.html()).not.toContain('deepseek-reasoner');

      await rendered.click(rendered.button('保存配置'));
      expect(providerMocks.saveMutation.mutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
        id: undefined,
        body: expect.objectContaining({
          baseUrl: preset.baseUrl,
          enabled: false,
          modelName: preset.model,
          providerName: preset.providerName,
          providerType: 'openai-compatible',
        }),
      }));
      rendered.unmount();
    }
  });
});

async function renderClient(element: ReactElement) {
  const actEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
  };
  const previousActEnvironment = actEnvironment.IS_REACT_ACT_ENVIRONMENT;
  actEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);

  await act(async () => {
    root.render(element);
  });

  return {
    button(label: string) {
      const button = Array.from(container.querySelectorAll('button')).find((node) => node.textContent?.includes(label) && !node.disabled);
      if (!button) throw new Error(`Button ${label} not found`);
      return button as HTMLButtonElement;
    },
    disabledButton(label: string) {
      return Array.from(container.querySelectorAll('button')).find((node) => node.textContent?.includes(label) && node.disabled) as HTMLButtonElement | undefined;
    },
    async changeInput(input: HTMLInputElement, value: string) {
      await act(async () => {
        const valueSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
        valueSetter?.call(input, value);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
      });
    },
    async click(button: HTMLButtonElement) {
      await act(async () => {
        button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      });
    },
    html() {
      return container.innerHTML;
    },
    input(placeholder: string) {
      const input = Array.from(container.querySelectorAll('input')).find((node) => node.placeholder === placeholder);
      if (!input) throw new Error(`Input ${placeholder} not found`);
      return input as HTMLInputElement;
    },
    unmount() {
      act(() => {
        root.unmount();
      });
      container.remove();
      actEnvironment.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
    },
  };
}
