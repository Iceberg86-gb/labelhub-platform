import { afterEach, describe, expect, it, vi } from 'vitest';
import { generateId } from './generateId';

const uuidV4Pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('generateId', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns a standard UUID v4 string', () => {
    const id = generateId();

    expect(id).toHaveLength(36);
    expect(id).toMatch(uuidV4Pattern);
  });

  it('falls back to getRandomValues when randomUUID is unavailable', () => {
    let seed = 0;
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.forEach((_, index) => {
          bytes[index] = (seed + index) & 0xff;
        });
        seed += 17;
        return bytes;
      },
    });

    const first = generateId();
    const second = generateId();

    expect(first).toHaveLength(36);
    expect(first).toMatch(uuidV4Pattern);
    expect(second).toHaveLength(36);
    expect(second).toMatch(uuidV4Pattern);
    expect(second).not.toBe(first);
  });
});
