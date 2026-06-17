// @vitest-environment node

import { describe, expect, it } from 'vitest';
import viteConfig from '../../../vite.config';

describe('vite dev server config', () => {
  it('binds the dev server to the IPv4 loopback address used by the README URL', () => {
    expect(viteConfig.server?.host).toBe('127.0.0.1');
    expect(viteConfig.server?.port).toBe(5173);
  });
});
