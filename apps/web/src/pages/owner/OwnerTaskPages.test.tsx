import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

const taskListQueryMock = vi.hoisted(() => vi.fn());
const taskDetailQueryMock = vi.hoisted(() => vi.fn());
const taskTransitionsQueryMock = vi.hoisted(() => vi.fn());
const taskWorkflowProgressQueryMock = vi.hoisted(() => vi.fn());

function textFrom(value: ReactNode) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

vi.mock('@douyinfe/semi-icons', () => ({
  IconArrowLeft: () => <span />,
  IconBolt: () => <span />,
  IconDelete: () => <span />,
  IconEdit: () => <span />,
  IconExternalOpen: () => <span />,
  IconPlus: () => <span />,
  IconRefresh: () => <span />,
}));

function MockTimeline({ children }: { children?: ReactNode }) {
  return <ol>{children}</ol>;
}

MockTimeline.Item = ({ children, time }: { children?: ReactNode; time?: ReactNode }) => (
  <li>
    {time}
    {children}
  </li>
);

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <button className={className}>{children}</button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>{title}{description}</div>
  ),
  Pagination: () => <nav />,
  Popconfirm: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Select: ({ value }: { value?: ReactNode }) => <div>{value}</div>,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Spin: () => <div />,
  Table: ({ columns, dataSource }: { columns: Array<any>; dataSource: Array<any> }) => (
    <table>
      <tbody>
        {dataSource.map((record) => (
          <tr key={record.id}>
            {columns.map((column, index) => (
              <td key={column.dataIndex ?? index}>
                {column.render ? column.render(record[column.dataIndex], record) : textFrom(record[column.dataIndex])}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Timeline: MockTimeline,
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Tooltip: ({ children }: { children?: ReactNode }) => <>{children}</>,
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
  useNavigate: () => vi.fn(),
  useParams: () => ({ taskId: '42' }),
  useSearchParams: () => [new URLSearchParams(), vi.fn()],
}));

vi.mock('../../features/task/create-task/CreateTaskModal', () => ({
  CreateTaskModal: () => null,
}));

vi.mock('../../features/task/delete-task/useDeleteTaskMutation', () => ({
  useDeleteTaskMutation: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
    variables: undefined,
  }),
}));

vi.mock('../../features/task/list-tasks/useTasksQuery', () => ({
  useTasksQuery: taskListQueryMock,
}));

vi.mock('../../features/dataset/DatasetUploadSection', () => ({
  DatasetUploadSection: () => <div>Dataset upload</div>,
}));

vi.mock('../../features/export/TrustedExportCard', () => ({
  TrustedExportCard: () => <section>Trusted export</section>,
}));

vi.mock('../../features/ai-review-rule/AiReviewRuleEntryCard', () => ({
  AiReviewRuleEntryCard: () => <section>AI review rule</section>,
}));

vi.mock('../../features/ai-review-rule/AiReviewRuleEditorDrawer', () => ({
  AiReviewRuleEditorDrawer: () => null,
}));

vi.mock('../../features/ai/TaskAiPrereviewPanel', () => ({
  TaskAiPrereviewPanel: () => <section>AI prereview panel</section>,
}));

vi.mock('../../features/schema-design/taskSchemaNavigation', () => ({
  buildTaskSchemaDraft: vi.fn(),
  findSchemaForTask: () => undefined,
}));

vi.mock('../../features/schema-design/useCreateSchemaMutation', () => ({
  useCreateSchemaMutation: () => ({ isPending: false, mutateAsync: vi.fn() }),
}));

vi.mock('../../features/schema-design/useSchemasQuery', () => ({
  useSchemasQuery: () => ({ data: { items: [] }, isError: false, isLoading: false }),
}));

vi.mock('../../features/submission/OwnerTaskSubmissionsSection', () => ({
  OwnerTaskSubmissionsSection: () => <section>Submissions</section>,
}));

vi.mock('../../features/task/task-detail/TaskNextStepGuidance', () => ({
  TaskNextStepGuidance: () => <section>Next steps</section>,
}));

vi.mock('../../features/task/task-detail/useTaskDetailQuery', () => ({
  useTaskDetailQuery: taskDetailQueryMock,
}));

vi.mock('../../features/task/task-transitions/useTaskTransitionsQuery', () => ({
  useTaskTransitionsQuery: taskTransitionsQueryMock,
}));

vi.mock('../../features/task/workflow-progress/useTaskWorkflowProgressQuery', () => ({
  useTaskWorkflowProgressQuery: taskWorkflowProgressQueryMock,
}));

vi.mock('../../features/task/transition-task/TransitionButtons', () => ({
  TransitionButtons: () => <div>Transition buttons</div>,
}));

vi.mock('../../features/task/transition-task/TransitionTaskModal', () => ({
  TransitionTaskModal: () => null,
}));

vi.mock('../../features/task/transition-task/transitionRules', () => ({
  transitionLabels: {
    draft: '草稿',
    ended: '已结束',
    paused: '已暂停',
    published: '发布中',
  },
}));

vi.mock('../../features/task/update-task/EditTaskModal', () => ({
  EditTaskModal: () => null,
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: () => ({ displayName: 'Owner Demo', id: 7 }),
}));

import { OwnerTaskDetailPage } from './OwnerTaskDetailPage';
import { OwnerTasksListPage } from './OwnerTasksListPage';

const demoTask = {
  id: 42,
  title: '客服对话质检',
  description: '审核多轮对话的意图和风险标签。',
  status: 'draft',
  quotaClaimed: 12,
  quotaTotal: 80,
  deadlineAt: '2026-06-10T12:00:00Z',
  createdAt: '2026-05-20T09:00:00Z',
  tags: ['客服', '质检'],
  instructionRichText: '按字段说明完成标注。',
  rewardRule: { unit: 'item' },
};

describe('Owner task pages design shell', () => {
  it('renders the task list with an owner overview and table surface', () => {
    taskListQueryMock.mockReturnValue({
      data: { items: [demoTask], total: 1 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });

    const html = renderToString(<OwnerTasksListPage />);

    expect(html).toContain('owner-task-page');
    expect(html).toContain('owner-task-overview');
    expect(html).toContain('owner-task-stat');
    expect(html).toContain('task-table-surface task-table-surface--owner');
    expect(html).toContain('客服对话质检');
    expect(html).toContain('创建任务');
  });

  it('renders the task detail as a command center with summary rail', () => {
    taskDetailQueryMock.mockReturnValue({
      data: demoTask,
      error: null,
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    taskTransitionsQueryMock.mockReturnValue({
      data: [
        {
          actorId: 7,
          createdAt: '2026-05-21T09:00:00Z',
          fromStatus: undefined,
          id: 1,
          reason: '初始创建',
          toStatus: 'draft',
        },
      ],
      isLoading: false,
      refetch: vi.fn(),
    });
    taskWorkflowProgressQueryMock.mockReturnValue({
      data: {
        aiPrereviewCompletedCount: 8,
        approvedCount: 2,
        labelingCount: 1,
        pendingReviewCount: 3,
        pendingSeniorReviewCount: 4,
        quotaClaimed: 12,
        quotaTotal: 80,
        rejectedCount: 1,
        submittedCount: 10,
        taskId: 42,
        unclaimedCount: 68,
      },
      isLoading: false,
      refetch: vi.fn(),
    });

    const html = renderToString(<OwnerTaskDetailPage />);

    expect(html).toContain('task-detail-page task-detail-page--owner');
    expect(html).toContain('owner-task-command-center');
    expect(html).toContain('owner-task-summary-grid');
    expect(html).toContain('owner-workflow-progress-card');
    expect(html).toContain('全过程进度');
    expect(html).toContain('AI prereview panel');
    expect(html).toContain('待领取');
    expect(html).toContain('待复审');
    expect(html).toContain('任务状态日志');
    expect(html).toContain('仅记录发布、暂停、恢复或结束等任务状态变化。');
    expect(html).toContain('状态操作');
    expect(html).toContain('Owner 可从这里进入历史模板（Schema）作答与 AI 预审。');
    expect(html).toContain('客服对话质检');
  });

});
