import { describe, expect, it, vi } from 'vitest';

vi.mock('../../shared/api/client', () => ({ apiClient: {} }));

const { exportModeFor } = await import('../../features/export/useCreateExportMutation');

describe('export admission mode (ADR-020)', () => {
  it('exports human-approved data only unless the owner explicitly opts in to policy-admitted data', () => {
    expect(exportModeFor(undefined)).toBe('approved_only');
    expect(exportModeFor(false)).toBe('approved_only');
    expect(exportModeFor(true)).toBe('approved_with_policy');
  });
});
