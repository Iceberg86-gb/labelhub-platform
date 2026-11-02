# M7-P1 After Screenshot Set

Purpose: after-screenshot set for the M7-P1 Owner audit log evidence surface.

Capture metadata:

- Date: 2026-05-27
- Browser: Codex in-app browser against local Vite/API
- Logical viewport during capture: 1440 x 900 for screenshots 01-03; 1024 x 900 for screenshot 04
- Image dimensions: 1440 x 900 PNG for screenshots 01-03; 1024 x 900 PNG for screenshot 04
- Capture mode: local Vite/API, `owner_demo`, M7-P1 code head `c3d8206`

## Screenshot Map

| File | Defense path | Audit IDs verified |
|------|--------------|--------------------|
| `01-owner-audit-logs-list-after.png` | M7-P1 query page | 11 implemented action types as filter options; mixed audit rows visible |
| `02-owner-audit-logs-payload-modal-after.png` | M7-P1 payload preview | Full JSON + canonical `payload_hash` visible |
| `03-owner-audit-logs-csv-export-after.png` | M7-P1 CSV export | Export control visible; CSV endpoint verified separately with HTTP 200 + `Content-Disposition` filename |
| `04-owner-audit-logs-narrow-viewport-1024-after.png` | M7-P1 Cluster 5b responsive verification | Filter bar wraps cleanly; no horizontal page overflow at 1024px |

## D-口径 Notes

- Codex in-app browser does not support browser download events. Attempting to click `导出 CSV` triggered the runtime's download guard before a browser download notification could be captured.
- CSV export was verified separately against the local API with `GET /api/audit-logs/export.csv`: response status `200`, `Content-Disposition: attachment; filename="audit-logs-20260527-214130.csv"`, `Content-Type: text/csv;charset=utf-8`, and the expected CSV header row.
- `03-owner-audit-logs-csv-export-after.png` therefore records the visible export entry point, while the HTTP verification above records the actual download contract.
- `04-owner-audit-logs-narrow-viewport-1024-after.png` is the visual reference for the 3-viewport sanity standard introduced in Cluster 5b. M7-P2 and later frontend clusters should capture similar narrow-viewport evidence.
