# Task Schema Entry Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the task detail "设计 Schema" step create or reuse the task-bound schema and open Designer directly, so newly created tasks reliably appear in the Schema management workflow.

**Architecture:** Keep schema creation task-scoped. The task detail page becomes the owner-facing entry: it looks for an existing schema bound to the task, creates one if absent, invalidates task/schema queries, and navigates to `/owner/schemas/{schemaId}/design`. The OpenAPI contract is corrected so `CreateSchemaRequest.taskId` is required, matching backend behavior.

**Tech Stack:** React, TanStack Query, generated OpenAPI types, Spring/OpenAPI contract, existing LabelHub schema APIs.

---

## Root Cause

- `OwnerTaskDetailPage` passes `onNavigateToSchema={() => navigate('/owner/schemas')}` into `TaskNextStepGuidance`, so the "去设计" CTA only opens the schema list.
- `CreateTaskModal` only calls `POST /tasks`; it never creates a schema family.
- `OwnerSchemasListPage` lists `GET /schemas` records. It cannot show a newly created task unless a schema family was also created for that task.
- Backend `SchemaService.create(taskId, ...)` requires a real task: omitting `taskId` currently returns `Task not found: null`.
- OpenAPI currently marks `CreateSchemaRequest.taskId` optional, which disagrees with backend behavior.

## File Map

- Modify `packages/contracts/openapi/labelhub.yaml`: require `taskId` in `CreateSchemaRequest`.
- Regenerate `apps/web/src/shared/api/generated/schema.d.ts`: generated type makes `taskId` required.
- Create `apps/web/src/features/schema-design/useCreateSchemaMutation.ts`: typed mutation for `POST /schemas`, query invalidation.
- Create `apps/web/src/features/schema-design/taskSchemaNavigation.ts`: pure helpers for finding task-bound schema and deriving default schema create input.
- Modify `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`: load schemas, create/reuse schema, navigate to Designer, show Toast/loading.
- Modify `apps/web/src/features/task/task-detail/TaskNextStepGuidance.tsx`: accept schema action loading/disabled state and clearer CTA copy.
- Test `apps/web/src/features/schema-design/taskSchemaNavigation.test.ts`: pure helper behavior.
- Test `apps/web/src/features/schema-design/useCreateSchemaMutation.test.ts` or nearest existing test pattern if API mocking is already available.
- Optionally add backend contract/API test if current backend test suite has an existing create-schema integration test surface.

---

### Task 1: Contract Alignment

**Files:**
- Modify: `packages/contracts/openapi/labelhub.yaml`
- Regenerate: `apps/web/src/shared/api/generated/schema.d.ts`

- [ ] **Step 1: Make `taskId` required in OpenAPI**

Change:

```yaml
CreateSchemaRequest:
  type: object
  required: [taskId, name]
  properties:
    taskId:
      type: integer
      format: int64
    name:
      type: string
    description:
      type: string
```

- [ ] **Step 2: Regenerate frontend API types**

Run:

```bash
pnpm --filter @labelhub/web gen:api
```

Expected: `CreateSchemaRequest.taskId` becomes non-optional in `apps/web/src/shared/api/generated/schema.d.ts`.

- [ ] **Step 3: Record MD5 baseline shift**

Run:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
```

Expected: MD5 changes from `890e595c6351ee53788d35354b2412a3`; report the new value as the post-fix contract anchor.

### Task 2: Schema Creation Mutation

**Files:**
- Create: `apps/web/src/features/schema-design/useCreateSchemaMutation.ts`

- [ ] **Step 1: Add typed mutation**

Implement:

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { schemaListQueryKey } from './useSchemasQuery';
import { taskDetailQueryKey } from '../task/task-detail/useTaskDetailQuery';
import { taskListQueryKey } from '../task/list-tasks/useTasksQuery';

export type CreateSchemaRequest = components['schemas']['CreateSchemaRequest'];
export type LabelSchema = components['schemas']['LabelSchema'];
type ApiFieldError = components['schemas']['ApiFieldError'];

export type CreateSchemaFailure = {
  message: string;
  fieldErrors?: ApiFieldError[];
};

export function useCreateSchemaMutation() {
  const queryClient = useQueryClient();

  return useMutation<LabelSchema, CreateSchemaFailure, CreateSchemaRequest>({
    mutationFn: async (body) => {
      const { data, error } = await apiClient.POST('/schemas', { body });
      if (error || !data) {
        throw {
          message: error?.message ?? 'Schema 创建失败。',
          fieldErrors: error?.fieldErrors,
        } satisfies CreateSchemaFailure;
      }
      return data;
    },
    onSuccess: async (schema) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: schemaListQueryKey(), exact: false }),
        queryClient.invalidateQueries({ queryKey: taskDetailQueryKey(schema.taskId ?? 0) }),
        queryClient.invalidateQueries({ queryKey: taskListQueryKey(), exact: false }),
      ]);
    },
  });
}
```

- [ ] **Step 2: Verify path imports**

Run:

```bash
pnpm --filter @labelhub/web typecheck
```

Expected at this stage may fail only if the hook imports need path adjustment; fix imports, not behavior.

### Task 3: Task-Scoped Schema Helpers

**Files:**
- Create: `apps/web/src/features/schema-design/taskSchemaNavigation.ts`
- Test: `apps/web/src/features/schema-design/taskSchemaNavigation.test.ts`

- [ ] **Step 1: Add helper tests first**

Test cases:

```ts
import { describe, expect, it } from 'vitest';
import { buildTaskSchemaDraft, findSchemaForTask } from './taskSchemaNavigation';

describe('task schema navigation helpers', () => {
  it('finds an existing schema bound to a task', () => {
    const schema = findSchemaForTask([
      { id: 7, taskId: 22, name: 'Task Schema', ownerId: 1, createdAt: '2026-05-28T00:00:00Z' },
      { id: 8, taskId: 23, name: 'Other Schema', ownerId: 1, createdAt: '2026-05-28T00:00:00Z' },
    ], 22);
    expect(schema?.id).toBe(7);
  });

  it('returns undefined when no schema is bound', () => {
    expect(findSchemaForTask([], 22)).toBeUndefined();
  });

  it('builds a task-bound schema create request', () => {
    expect(buildTaskSchemaDraft({ id: 22, title: '风险标注', description: 'demo' })).toEqual({
      taskId: 22,
      name: '风险标注 Schema',
      description: 'demo',
    });
  });
});
```

- [ ] **Step 2: Implement helpers**

```ts
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
```

- [ ] **Step 3: Run helper tests**

```bash
pnpm --filter @labelhub/web test -- taskSchemaNavigation
```

Expected: new helper tests pass.

### Task 4: Task Detail Schema CTA

**Files:**
- Modify: `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`
- Modify: `apps/web/src/features/task/task-detail/TaskNextStepGuidance.tsx`

- [ ] **Step 1: Add `TaskNextStepGuidance` action state props**

Add props:

```ts
type TaskNextStepGuidanceProps = {
  task: Task;
  onNavigateToSchema: () => void;
  onScrollToDataset: () => void;
  onPublish: () => void;
  schemaActionLoading?: boolean;
  schemaActionDisabled?: boolean;
};
```

Use them on the schema button only:

```tsx
<Button
  disabled={step.disabled || (step.id === 'schema' && schemaActionDisabled)}
  loading={step.id === 'schema' ? schemaActionLoading : false}
  size="small"
  theme={step.id === 'publish' ? 'solid' : 'light'}
  type={step.id === 'publish' ? 'primary' : 'tertiary'}
  onClick={step.onClick}
>
  {step.ctaLabel}
</Button>
```

- [ ] **Step 2: Wire existing-or-create behavior in task detail**

In `OwnerTaskDetailPage`, import:

```ts
import { Toast } from '@douyinfe/semi-ui';
import { useCreateSchemaMutation } from '../../features/schema-design/useCreateSchemaMutation';
import { schemaListQueryKey, useSchemasQuery } from '../../features/schema-design/useSchemasQuery';
import { buildTaskSchemaDraft, findSchemaForTask } from '../../features/schema-design/taskSchemaNavigation';
```

Add query/mutation:

```ts
const schemasQuery = useSchemasQuery({ page: 1, size: 100 });
const createSchemaMutation = useCreateSchemaMutation();
const taskSchema = task ? findSchemaForTask(schemasQuery.data?.items ?? [], task.id) : undefined;
```

Replace current `onNavigateToSchema={() => navigate('/owner/schemas')}` with:

```ts
const openTaskSchemaDesigner = async () => {
  if (!task) return;
  const existing = taskSchema;
  if (existing) {
    navigate(`/owner/schemas/${existing.id}/design`);
    return;
  }

  try {
    const created = await createSchemaMutation.mutateAsync(buildTaskSchemaDraft(task));
    Toast.success('已创建任务 Schema。');
    navigate(`/owner/schemas/${created.id}/design`);
  } catch (error) {
    const message = typeof error === 'object' && error && 'message' in error
      ? String((error as { message?: unknown }).message)
      : 'Schema 创建失败。';
    Toast.error(message);
  }
};
```

Pass:

```tsx
<TaskNextStepGuidance
  task={task}
  onNavigateToSchema={openTaskSchemaDesigner}
  onScrollToDataset={scrollToDataset}
  onPublish={() => setTargetStatus('published')}
  schemaActionLoading={createSchemaMutation.isPending || schemasQuery.isFetching}
  schemaActionDisabled={schemasQuery.isError}
/>
```

- [ ] **Step 3: Keep Schema list semantics unchanged**

Do not add a generic create button to `OwnerSchemasListPage` in this fix. The source of truth is task detail, because schema creation is task-bound.

### Task 5: Optional Empty-State Copy

**Files:**
- Modify: `apps/web/src/pages/owner/OwnerSchemasListPage.tsx`

- [ ] **Step 1: Update stale copy only**

Change empty state description from:

```tsx
description="Schema 创建入口将在后续阶段接入任务详情页。"
```

to:

```tsx
description="请先进入任务详情页,点击“去设计”创建并绑定 Schema。"
```

### Task 6: Verification

**Commands:**

- [ ] **Step 1: Frontend typecheck**

```bash
pnpm --filter @labelhub/web typecheck
```

Expected: exit 0.

- [ ] **Step 2: Frontend tests**

```bash
pnpm --filter @labelhub/web test
```

Expected: existing tests plus new helper tests pass.

- [ ] **Step 3: Frontend build**

```bash
pnpm --filter @labelhub/web build
```

Expected: exit 0.

- [ ] **Step 4: Protected endpoint check**

```bash
bash scripts/check-protected-endpoints.sh
```

Expected: exit 0.

- [ ] **Step 5: Manual browser acceptance**

Use owner demo account:

1. Create a new task.
2. Open task detail.
3. Click `去设计`.
4. Expected: app creates a schema and navigates to `/owner/schemas/{id}/design`.
5. Return to `Schema 管理`.
6. Expected: the schema row is visible and bound to the task.
7. Click `去设计` again from task detail.
8. Expected: it reuses the same schema, no duplicate row.

## Scope Guardrails

- Do not change P3b linkage DSL/evaluator/corpus logic.
- Do not change `SchemaFormilyRenderer`, `SchemaRenderer`, or labeler submission pages.
- Do not add migrations; current schema table already supports `task_id`.
- Do not create schemas automatically in `CreateTaskModal`; creation should happen when the owner chooses the schema design step.
- Do not create duplicate schemas for the same task from the detail CTA.

## Open Questions For Review

1. Should this fix also add a backend uniqueness guard for one schema family per task? The current table has no unique index on `label_schemas.task_id`. The frontend can avoid duplicates by reusing `GET /schemas`, but true global protection would require a migration and backend behavior decision.
2. Should Schema list gain a task filter or task title column? Not required for the broken entry path, but it would make the task-schema relationship clearer.
3. Should `TaskNextStepGuidance` show "编辑 Schema" instead of "查看 Schema" after a draft schema exists but before publish? The plan keeps existing language except loading/disabled state.
