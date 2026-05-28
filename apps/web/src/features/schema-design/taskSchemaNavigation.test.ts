import { describe, expect, it } from 'vitest';
import { buildTaskSchemaDraft, findSchemaForTask } from './taskSchemaNavigation';

describe('taskSchemaNavigation', () => {
  const createdAt = '2026-05-28T00:00:00Z';

  it('finds an existing schema bound to a task', () => {
    const schema = findSchemaForTask(
      [
        { id: 7, taskId: 22, name: 'Task Schema', ownerId: 1, createdAt },
        { id: 8, taskId: 23, name: 'Other Schema', ownerId: 1, createdAt },
      ],
      22,
    );

    expect(schema?.id).toBe(7);
  });

  it('returns undefined when no schema is bound to the task', () => {
    expect(findSchemaForTask([], 22)).toBeUndefined();
    expect(findSchemaForTask([{ id: 8, taskId: 23, name: 'Other Schema', ownerId: 1, createdAt }], 22)).toBeUndefined();
  });

  it('builds a task-bound schema create request', () => {
    expect(buildTaskSchemaDraft({ id: 22, title: '风险标注', description: 'demo' })).toEqual({
      taskId: 22,
      name: '风险标注 Schema',
      description: 'demo',
    });
  });
});
