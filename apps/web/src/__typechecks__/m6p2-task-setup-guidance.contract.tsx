import type { components } from '../shared/api/generated/schema';

type Task = components['schemas']['Task'];

type SetupStepId = 'schema' | 'dataset' | 'publish';

type SetupStep = {
  id: SetupStepId;
  ready: boolean;
  disabled?: boolean;
};

function deriveSetupSteps(task: Task): [SetupStep, SetupStep, SetupStep] {
  const schemaReady = task.currentSchemaVersionId != null;
  const datasetReady = task.currentDatasetId != null;

  return [
    { id: 'schema', ready: schemaReady },
    { id: 'dataset', ready: datasetReady },
    { id: 'publish', ready: task.status !== 'draft', disabled: !(schemaReady && datasetReady) },
  ];
}

export function M6P2TaskSetupGuidanceContract() {
  const task: Task = {
    id: 1,
    title: 'Setup guidance task',
    deadlineAt: '2026-05-25T00:00:00Z',
    quotaTotal: 1,
    quotaClaimed: 0,
    status: 'draft',
    currentSchemaVersionId: null,
    currentDatasetId: null,
  };
  const steps = deriveSetupSteps(task);
  steps satisfies [SetupStep, SetupStep, SetupStep];
  return null;
}
