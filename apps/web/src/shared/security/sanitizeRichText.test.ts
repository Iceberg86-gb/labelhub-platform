import { describe, expect, it } from 'vitest';
import { sanitizeRichTextHtml } from './sanitizeRichText';

describe('sanitizeRichTextHtml', () => {
  it('strips <script> tags', () => {
    const out = sanitizeRichTextHtml('<p>hi</p><script>alert(1)</script>');
    expect(out).toContain('<p>hi</p>');
    expect(out.toLowerCase()).not.toContain('<script');
    expect(out).not.toContain('alert(1)');
  });

  it('removes onerror / event-handler attributes', () => {
    const out = sanitizeRichTextHtml('<img src=x onerror="alert(1)">');
    expect(out.toLowerCase()).not.toContain('onerror');
    // <img> is not in the allowlist, so it is dropped entirely.
    expect(out.toLowerCase()).not.toContain('<img');
  });

  it('drops javascript: links', () => {
    const out = sanitizeRichTextHtml('<a href="javascript:alert(1)">x</a>');
    expect(out.toLowerCase()).not.toContain('javascript:');
  });

  it('forces rel="noopener noreferrer" on surviving links', () => {
    const out = sanitizeRichTextHtml('<a href="https://example.com">x</a>');
    expect(out).toContain('href="https://example.com"');
    expect(out).toContain('rel="noopener noreferrer"');
  });

  it('preserves legitimate rich-text formatting', () => {
    const input = '<p><strong>bold</strong> and <em>italic</em></p><ul><li>one</li><li>two</li></ul>';
    const out = sanitizeRichTextHtml(input);
    expect(out).toContain('<strong>bold</strong>');
    expect(out).toContain('<em>italic</em>');
    expect(out).toContain('<li>one</li>');
    expect(out).toContain('<li>two</li>');
  });

  it('returns empty string for non-string / empty input', () => {
    expect(sanitizeRichTextHtml(undefined)).toBe('');
    expect(sanitizeRichTextHtml(null)).toBe('');
    expect(sanitizeRichTextHtml(123)).toBe('');
    expect(sanitizeRichTextHtml('')).toBe('');
  });
});
