import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchDefaultPromptVersion } from './useDefaultPromptVersionQuery';

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    GET: getMock,
  },
}));

describe('fetchDefaultPromptVersion', () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it('fetches the default prompt version from the prompt versions endpoint', async () => {
    const promptVersion = {
      id: 1,
      versionNo: 1,
      contentHash: 'a'.repeat(64),
      content: 'm3-owner-review-v1',
      status: 'published',
      createdAt: '2026-05-28T00:00:00Z',
    };
    getMock.mockResolvedValueOnce({ data: promptVersion, error: undefined, response: { status: 200 } });

    await expect(fetchDefaultPromptVersion()).resolves.toEqual(promptVersion);
    expect(getMock).toHaveBeenCalledWith('/prompt-versions/default');
  });
});
