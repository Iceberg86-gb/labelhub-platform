import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@douyinfe/semi-icons', () => ({
  IconRefresh: () => <span />,
  IconSave: () => <span />,
  IconTickCircle: () => <span />,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <button className={className}>{children}</button>
  ),
  Input: ({ mode, placeholder, value }: { mode?: string; placeholder?: string; value?: ReactNode }) => (
    <input placeholder={placeholder} readOnly type={mode === 'password' ? 'password' : 'text'} value={String(value ?? '')} />
  ),
  Select: ({ optionList, value }: { optionList?: Array<{ label: string; value: string }>; value?: string }) => (
    <select value={value} onChange={() => undefined}>
      {optionList?.map((option) => (
        <option key={option.value} value={option.value}>{option.label}</option>
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
  TextArea: ({ value }: { value?: ReactNode }) => <textarea readOnly value={String(value ?? '')} />,
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

import { OwnerLlmSettingsPage } from './OwnerLlmSettingsPage';

describe('OwnerLlmSettingsPage', () => {
  it('renders the owner LLM connection console with provider, key, model, and assistive scope controls', () => {
    const queryClient = new QueryClient();
    const html = renderToString(
      <QueryClientProvider client={queryClient}>
        <OwnerLlmSettingsPage />
      </QueryClientProvider>,
    );

    expect(html).toContain('llm-settings-page');
    expect(html).toContain('LLM 接入');
    expect(html).toContain('Provider 配置');
    expect(html).toContain('Designer AI 交互字段与字段级建议');
    expect(html).toContain('API Key');
    expect(html).toContain('模型预设');
    expect(html).toContain('gpt-4.1-mini');
    expect(html).toContain('AI 预审辅助');
    expect(html).toContain('Designer AI 交互字段');
    expect(html).toContain('自动终审');
    expect(html).toContain('人工审核保留最终裁决权');
  });
});
