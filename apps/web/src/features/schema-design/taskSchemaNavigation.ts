import type { LabelSchema } from './useSchemasQuery';

export type TaskSchemaDraftSource = {
  id: number;
  title: string;
  description?: string;
};

export function findSchemaForTask(schemas: LabelSchema[], taskId: number) {
  return schemas.find((schema) => schema.taskId === taskId);
}

export function buildTaskSchemaDraft(task: TaskSchemaDraftSource) {
  return {
    taskId: task.id,
    name: `${task.title} Schema`,
    description: task.description || `Schema for task #${task.id}`,
  };
}
