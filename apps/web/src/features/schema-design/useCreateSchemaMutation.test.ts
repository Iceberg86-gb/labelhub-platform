import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createSchema } from './useCreateSchemaMutation';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

describe('createSchema', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('posts a task-bound schema create request', async () => {
    const schema = {
      id: 9,
      taskId: 22,
      name: '风险标注 Schema',
      ownerId: 1,
      createdAt: '2026-05-28T00:00:00Z',
    };
    postMock.mockResolvedValueOnce({ data: schema, error: undefined });

    await expect(createSchema({ taskId: 22, name: '风险标注 Schema', description: 'demo' })).resolves.toEqual(schema);
    expect(postMock).toHaveBeenCalledWith('/schemas', {
      body: { taskId: 22, name: '风险标注 Schema', description: 'demo' },
    });
  });

  it('preserves schema create field errors', async () => {
    const fieldErrors = [{ field: 'taskId', message: '任务不存在' }];
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: {
        code: 'NOT_FOUND',
        message: 'Task not found: 22',
        fieldErrors,
      },
    });

    await expect(createSchema({ taskId: 22, name: '风险标注 Schema' })).rejects.toEqual({
      message: 'Task not found: 22',
      fieldErrors,
    });
  });
});
