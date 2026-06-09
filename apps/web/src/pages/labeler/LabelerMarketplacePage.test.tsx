import type { ReactElement, ReactNode } from 'react';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

const marketplaceQueryMock = vi.hoisted(() => vi.fn());
const claimMutateMock = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({
  navigate: vi.fn(),
  searchParams: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconChevronLeft: () => <span />,
  IconChevronRight: () => <span />,
  IconPlay: () => <span />,
  IconRefresh: () => <span />,
  IconSearch: () => <span />,
}));

function MockSelect({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={className}>{children}</div>;
}

MockSelect.Option = ({ children }: { children?: ReactNode }) => <span>{children}</span>;

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({
    children,
    className,
    icon,
    onClick,
  }: {
    children?: ReactNode;
    className?: string;
    icon?: ReactNode;
    onClick?: () => void;
  }) => (
    <button className={className} onClick={onClick}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Input: ({ value, placeholder }: { value?: string; placeholder?: string }) => (
    <input placeholder={placeholder} value={value} readOnly />
  ),
  InputNumber: ({
    'aria-label': ariaLabel,
    max,
    onChange,
    value,
  }: {
    'aria-label'?: string;
    max?: number;
    onChange?: (value: number) => void;
    value?: number;
  }) => (
    <input
      aria-label={ariaLabel}
      data-max={max}
      type="number"
      value={value}
      onChange={(event) => onChange?.(Number(event.currentTarget.value))}
      onInput={(event) => onChange?.(Number(event.currentTarget.value))}
    />
  ),
  Select: MockSelect,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Spin: () => <div />,
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  Typography: {
    Paragraph: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <p className={className}>{children}</p>
    ),
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => routeState.navigate,
  useSearchParams: () => [routeState.searchParams, routeState.setSearchParams],
}));

vi.mock('../../features/labeling/useMarketplaceQuery', () => ({
  useMarketplaceQuery: marketplaceQueryMock,
}));

vi.mock('../../features/labeling/useClaimMutation', () => ({
  ClaimTaskFailure: class ClaimTaskFailure extends Error {},
  useClaimBatchMutation: () => ({ isPending: false, mutateAsync: claimMutateMock }),
  useClaimMutation: () => ({ isPending: false, mutateAsync: claimMutateMock }),
}));

vi.mock('../../shared/ui/RoleBadge', () => ({
  RoleBadge: ({ role }: { role: string }) => <span>{role}</span>,
}));

import { LabelerMarketplacePage } from './LabelerMarketplacePage';

const marketplaceTask = {
  availableItemCount: 8,
  deadlineAt: '2026-06-01T12:00:00Z',
  description: '判断客服回复是否合规。',
  id: 22,
  instructionRichText: '按质检标准阅读任务说明后再领取。',
  quotaClaimed: 4,
  quotaTotal: 20,
  rewardRule: '每条通过审核后计入奖励。',
  tags: ['客服', '风控'],
  title: '客服回复质检',
};

afterEach(() => {
  marketplaceQueryMock.mockReset();
  claimMutateMock.mockReset();
  routeState.navigate.mockReset();
  routeState.setSearchParams.mockReset();
  routeState.searchParams = new URLSearchParams();
});

describe('LabelerMarketplacePage task detail drawer', () => {
  it('opens a labeler-safe detail drawer from marketplace item data and reuses claim', async () => {
    marketplaceQueryMock.mockReturnValue({
      data: { items: [marketplaceTask], total: 1 },
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    claimMutateMock.mockResolvedValue({ claimedCount: 8, requestedSize: 8, sessions: [{ id: 11 }] });

    const rendered = await renderClient(<LabelerMarketplacePage />);
    const detailButton = buttonByText('查看详情');
    expect(detailButton).toBeTruthy();

    await act(async () => {
      detailButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    const detailHtml = rendered.html();
    expect(detailHtml).toContain('任务详情');
    expect(detailHtml).toContain('客服回复质检');
    expect(detailHtml).toContain('按质检标准阅读任务说明后再领取。');
    expect(detailHtml).toContain('每条通过审核后计入奖励。');
    expect(detailHtml).toContain('可领取题目');
    expect(detailHtml).toContain('本次领取上限');
    expect(detailHtml).toContain('8 个');
    expect(detailHtml).not.toContain('answer_payload');
    expect(detailHtml).not.toContain('schemaJson');
    expect(detailHtml).not.toContain('ai_overall');
    expect(detailHtml).not.toContain('recommendation');
    expect(detailHtml).not.toContain('reviewer');

    await act(async () => {
      buttonByText('领取 8 条')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(claimMutateMock).toHaveBeenCalledWith({ size: 8, taskId: 22 });
    expect(routeState.navigate).toHaveBeenCalledWith('/labeler/sessions/11');

    rendered.unmount();
  });

  it('caps manually entered claim size to available item count', async () => {
    const taskWithMoreAvailableItems = { ...marketplaceTask, availableItemCount: 12, quotaClaimed: 0 };
    marketplaceQueryMock.mockReturnValue({
      data: { items: [taskWithMoreAvailableItems], total: 1 },
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    claimMutateMock.mockResolvedValue({ claimedCount: 12, requestedSize: 12, sessions: [{ id: 11 }] });

    const rendered = await renderClient(<LabelerMarketplacePage />);
    const sizeInput = document.querySelector<HTMLInputElement>('input[aria-label="领取客服回复质检数量"]');

    await act(async () => {
      if (sizeInput) {
        setInputValue(sizeInput, '99');
        sizeInput.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });
    await act(async () => {
      buttonByText('领取 12 条')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(claimMutateMock).toHaveBeenCalledWith({ size: 12, taskId: 22 });

    rendered.unmount();
  });
});

function buttonByText(text: string) {
  return Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.includes(text));
}

function setInputValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  setter?.call(input, value);
}

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
  await act(async () => {
    await Promise.resolve();
  });

  return {
    html: () => container.innerHTML,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
      actEnvironment.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
    },
  };
}
