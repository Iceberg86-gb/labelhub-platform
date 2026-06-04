import { beforeEach, describe, expect, it, vi } from 'vitest';
import { activateLlmProvider } from '../../features/llm-provider/useLlmProviders';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

describe('activateLlmProvider', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('posts through the generated activate provider path', async () => {
    const provider = {
      id: 9,
      scope: 'platform',
      providerType: 'openai-compatible',
      providerName: 'qwen',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      modelName: 'qwen-plus',
      enabled: true,
      hasSecret: true,
      createdAt: '2026-06-04T00:00:00Z',
      updatedAt: '2026-06-04T00:00:00Z',
    };
    postMock.mockResolvedValueOnce({ data: provider, error: undefined });

    await expect(activateLlmProvider(9)).resolves.toEqual(provider);

    expect(postMock).toHaveBeenCalledWith('/llm/providers/{providerConfigId}:activate', {
      params: { path: { providerConfigId: 9 } },
    });
  });

  it('surfaces backend activate errors such as provider_secret_missing', async () => {
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: {
        code: 'provider_secret_missing',
        message: 'provider_secret_missing',
      },
    });

    await expect(activateLlmProvider(9)).rejects.toThrow('provider_secret_missing');
  });
});
