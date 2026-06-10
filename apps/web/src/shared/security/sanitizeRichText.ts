import DOMPurify from 'dompurify';

/**
 * Single source of truth for sanitising rich-text HTML before it is injected via
 * `dangerouslySetInnerHTML`. Rich-text answers are authored by labelers and later rendered to
 * other roles (e.g. an owner reviewing a submission), so unsanitised HTML is a stored-XSS vector.
 *
 * Allowlist mirrors what the rich-text editor toolbar can actually produce (bold / italic /
 * underline / lists / links). Everything else — including `<script>`, `<img onerror=...>`, event
 * handlers and `javascript:` URLs — is stripped by DOMPurify.
 */
const ALLOWED_TAGS = ['p', 'br', 'b', 'strong', 'i', 'em', 'u', 'ul', 'ol', 'li', 'a'];
const ALLOWED_ATTR = ['href', 'target', 'rel'];

let hookRegistered = false;

function ensureAnchorHardeningHook(): void {
  if (hookRegistered) {
    return;
  }
  // DOMPurify already drops `javascript:`/`data:` hrefs and `on*` handlers; this additionally
  // forces safe rel on any surviving anchor so links can never reach window.opener.
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && node.hasAttribute('href')) {
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
  hookRegistered = true;
}

/**
 * Sanitise untrusted rich-text HTML to a safe subset. Returns an empty string for non-string or
 * empty input so callers can pass raw field values directly.
 */
export function sanitizeRichTextHtml(value: unknown): string {
  if (typeof value !== 'string' || value.length === 0) {
    return '';
  }
  ensureAnchorHardeningHook();
  return DOMPurify.sanitize(value, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
  });
}
