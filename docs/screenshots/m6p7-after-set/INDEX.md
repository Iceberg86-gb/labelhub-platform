# M6-P7 After Screenshot Set

Purpose: after-screenshot set for the M6-P7 Owner task hard delete capability.

Capture metadata:

- Date: 2026-05-27
- Browser: Google Chrome headless via local Chrome DevTools Protocol
- Logical viewport during capture: 2048 x 1280
- Image dimensions: 2048 x 1280 PNG
- Capture mode: local Vite/API, `owner_demo`, temporary visual task created and deleted for the capture path
- Code head during capture: `4555148` (`fix(frontend): keep task delete confirmation in viewport`)

## Screenshot Map

| File | Defense path | Audit IDs verified |
|------|--------------|--------------------|
| `01-owner-task-list-with-delete-after.png` | Owner task management | M6-P7 delete action visible on task rows |
| `02-owner-task-delete-popconfirm-after.png` | Owner task management | M6-P7 Popconfirm copy lists 6 data consumption categories |
| `03-owner-task-delete-success-toast-after.png` | Owner task management | M6-P7 Toast feedback + TanStack Query invalidation |

## D-口径 Notes

- `02-owner-task-delete-popconfirm-after.png` captures the post-Cluster-4b viewport fix: the destructive confirmation is positioned inside the visible viewport instead of drifting past the right edge.
- The Popconfirm copy explicitly lists `sessions、submissions、AI 调用、Quality Ledger、Verdict、Export 快照`, so the Owner provides informed consent before consuming task-scoped facts.
- This informed-consent UI is the user-facing safety net for the M6-P0.5 immutability override documented in `docs/internal/m6p7-verification.md`.
- `03-owner-task-delete-success-toast-after.png` shows the task list after deleting the temporary visual-capture task; the count decremented from 14 to 13 and the task row disappeared without a browser refresh.
