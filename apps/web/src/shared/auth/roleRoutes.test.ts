import { describe, expect, it } from 'vitest';
import { defaultPathForRoles } from './roleRoutes';

describe('roleRoutes', () => {
  it('routes any authenticated role set to the shared home page', () => {
    expect(defaultPathForRoles(['OWNER'])).toBe('/home');
    expect(defaultPathForRoles(['LABELER', 'REVIEWER'])).toBe('/home');
    expect(defaultPathForRoles(['OWNER', 'LABELER', 'SENIOR_REVIEWER'])).toBe('/home');
  });

  it('keeps users without roles out of the workspace shell', () => {
    expect(defaultPathForRoles([])).toBe('/forbidden');
  });
});
