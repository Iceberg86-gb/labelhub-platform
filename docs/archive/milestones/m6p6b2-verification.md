# M6-P6b2 Verification

## Status

M6-P6b2 P0 closed on 2026-05-26. M6-P6 phase is complete.

| Item | Value |
|------|-------|
| P6b1 baseline | `7d4d187` |
| P6b2 final code head | `5c03dcf` |
| Phase type | Frontend-only UI experience polish |
| Backend/OpenAPI/migration/test code changes | None |
| P1 entered | No |

## Commit Map

| Commit | Cluster | Purpose |
|--------|---------|---------|
| `496b0ef` | docs | scope + pre-estimate |
| `bd5854a` | C1 | schema version history drawer polish |
| `9b2aa09` | C1 | drawer mask deepen |
| `e6d8da9` | C2 | hide required marker on read-only submission detail |
| `a51c9c8` | C2 | submission AI stat cards + hash truncation |
| `2db36a1` | C2 | labeler historical render banner |
| `0a606f8` | C3 | trusted export list and diff modal polish |
| `34c2d94` | C4 | AI drawer idempotency surface polish |
| `fce451f` | C5 | reviewer queue ledger derivation surface |
| `577fef1` | C6 | owner setup blocked CTA differentiation |
| `5c03dcf` | nits | #32 three-hash card overflow fix + #38 AI suggestion tag duplication fix |

## Adjudications Applied

These adjudications travel with the P6b2 implementation record. They were not
retroactively written into the P6a audit snapshot.

- C2 #15 uses B+ banner copy: `此 submission 按提交时绑定的 Schema v{schemaVersion.versionNumber} 渲染,历史答案不会被新 schema 重写。`
- C3 #32 uses option A: explicit `✓ 一致` / `✗ 不一致` labels for the three boolean hash-match cards. `TruncatedHash` is used on the export list manifest hash and file-level SHA values, not on the three boolean cards.
- The supplemental P1 do-not-touch list was extended with #13, #16, #23, and #25 to prevent accidental scope drift on nearby surfaces.

The audit-time #22 mis-read and #28 demotion are recorded in P6a docs and are
not duplicated here.

## P0 Closure Table

| Audit ID | Commit | After screenshot | Status |
|----------|--------|------------------|--------|
| G-P0 #1 logo subtitle | `41096fa` | `01-owner-schema-typical-after.png` | Closed in P6b1 |
| G-P0 #2 TruncatedHash | `3ec160a`, `bd5854a`, `a51c9c8`, `0a606f8`, `34c2d94` | `02`, `03`, `07`, `08`, `09` | Closed |
| G-P0 #3 RoleBadge | `0313c26`, `fed07d7` | `01`, `03`, `05`, `10` | Closed in P6b1 |
| G-P0 #4 typography | `cd76f12` | All after screenshots | Closed in P6b1 |
| #7 schema version timeline/card hierarchy | `bd5854a` | `02-owner-schema-version-history-after.png` | Closed |
| #8 drawer mask | `9b2aa09` | `02-owner-schema-version-history-after.png` | Closed |
| #10 JSON toggle secondary styling | `bd5854a` | `02-owner-schema-version-history-after.png` | Closed |
| #11 required marker hidden on submitted detail | `e6d8da9` | `03`, `06` | Closed |
| #12 submission AI stat cards + hashes | `a51c9c8` | `03-labeler-submission-detail-after.png` | Closed |
| #15 historical render banner | `2db36a1` | `03`, `04` | Closed |
| #17 reviewer row hover | `fce451f` | `05-reviewer-queue-list-after.png` | Closed |
| #18 ledger-derived verdict subtitle | `fce451f` | `05-reviewer-queue-list-after.png` | Closed |
| #26 Trusted Export heading hierarchy | `0a606f8` | `07-export-snapshot-list-after.png` | Closed |
| #27 export manifest hash | `0a606f8` | `07-export-snapshot-list-after.png` | Closed |
| #31 export diff success banner | `0a606f8` | `08-export-diff-modal-after.png` | Closed |
| #32 three-hash match labels | `0a606f8`, `5c03dcf` | `08-export-diff-modal-after.png` | Closed after nits |
| #33 file-level SHA table | `0a606f8` | `08-export-diff-modal-after.png` | Closed |
| #35 idempotency-hit banner | `34c2d94` | `09-ai-drawer-idempotency-hit-after.png` | Closed |
| #36 AI drawer stat cards | `34c2d94` | `09-ai-drawer-idempotency-hit-after.png` | Closed |
| #37 AI drawer hashes | `34c2d94` | `09-ai-drawer-idempotency-hit-after.png` | Closed |
| #38 AI suggestion tag | `34c2d94`, `5c03dcf` | `09-ai-drawer-idempotency-hit-after.png` | Closed after nits |
| #40 blocked setup CTA lock | `577fef1` | `10-owner-setup-3cta-after.png` | Closed |

## TruncatedHash Integration Map

Verified with `rg -n "TruncatedHash"`.

| Audit ID | File | Line | Hash type |
|----------|------|------|-----------|
| #7 | `apps/web/src/features/schema-design/VersionHistoryDrawer.tsx` | 98 | schema `contentHash` |
| #12 | `apps/web/src/features/ai/AiProvenanceCard.tsx` | 66, 69 | submission AI input/output |
| #27 | `apps/web/src/features/export/TrustedExportCard.tsx` | 51 | export manifest hash |
| #33 | `apps/web/src/features/export/ExportSnapshotDiffModal.tsx` | 96 | file-level SHA-256 |
| #37 | `apps/web/src/features/ai/AiReviewDrawer.tsx` | 74, 75 | AI drawer input/output |

## Per-Cluster Pre-Estimate vs Actual

Actuals are from `git show <commit> --shortstat -- apps/web/src`. Changed lines
count insertions plus deletions.

| Cluster | Pre-estimate | Actual | Delta |
|---------|--------------|--------|-------|
| C1 | ~185 | 101 (`bd5854a` 93 + `9b2aa09` 8) | -84 |
| C2 | ~165 | 91 (`e6d8da9` 16 + `a51c9c8` 62 + `2db36a1` 13) | -74 |
| C3 | ~135 | 86 | -49 |
| C4 | ~105 | 69 | -36 |
| C5 | ~45 | 28 | -17 |
| C6 | ~25 | 14 | -11 |
| nits | n/a | 4 | n/a |

P6b2 actual frontend changed lines: 391.

P6b1 + P6b2 cumulative frontend diff from `a52f084`:

```text
19 files changed, 514 insertions(+), 59 deletions(-)
```

Cumulative changed frontend lines: 573 / 900 P0 cap.

## P1 Items Not Entered

P1 entry remains blocked pending separate user adjudication. The following P1
items were not implemented in M6-P6b2 P0:

- #13 submission detail tag color semantics
- #16 filter dropdown / refresh button style consistency
- #21 reviewer ledger timeline restructure
- #23 approve button success-green semantic
- #25 verdict source provenance line emphasis
- #28 export list same-hash indicator
- #29 / #30 export table column width / disabled-state polish
- #34 diff modal width
- #39 AI drawer field feedback as cards
- #42 / #44 owner setup spacing / empty-state copy

## Visual Verification

The full P6b2 after set is archived at
`docs/screenshots/m6p6-after-set/p6b2-full/`.

The auditor signed off on the visual evidence after reviewing the 10 screenshots.
Images `08-export-diff-modal-after.png` and
`09-ai-drawer-idempotency-hit-after.png` were re-captured after nits commit
`5c03dcf` to verify:

- #32: three-hash cards render full `✓ 一致` text instead of `✓ ...`.
- #38: AI suggestion renders as `AI 建议 [看起来良好]` without duplicated label text.

## Final Verification Commands

### Frontend Typecheck

Command:

```bash
pnpm --filter @labelhub/web typecheck
```

Result:

- Exit code: 0.
- `openapi-typescript` regenerated the local generated schema from the unchanged OpenAPI contract.

### Frontend Build

Command:

```bash
pnpm --filter @labelhub/web build
```

Result:

- Exit code: 0.
- Vite completed with the existing large-chunk warning:
  `Some chunks are larger than 500 kB after minification`.
- Final build line: `✓ built in 2.76s`.

### Protected Endpoint Guard

Command:

```bash
bash scripts/check-protected-endpoints.sh
```

Result:

```text
Protected OpenAPI endpoints are present.
```

### Backend Full Suite

Command:

```bash
mvn -pl services/api test
```

Result:

- Initial sandbox run failed with known local socket D-口径:
  `SocketException: Operation not permitted` while opening local MySQL/test HTTP sockets.
- Escalated rerun passed.

```text
Tests run: 390, Failures: 0, Errors: 0, Skipped: 78
BUILD SUCCESS
```

### OpenAPI MD5

Command:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
```

Result:

```text
c042f8bc62a15efd98bd01363b9e14ff
```

### Migration Count

Command:

```bash
find services/api/src/main/resources/db/migration -maxdepth 1 -name 'V*.sql' | wc -l
```

Result:

```text
10
```

## No-Touch Confirmation

Command:

```bash
git diff a52f084..HEAD --name-only -- services/ packages/contracts/ db/migration/ services/api/src/main/resources/db/migration/
```

Result: no paths.

M6-P6b2 did not touch backend code, OpenAPI contracts, migrations, or tests.

## Audit Doc Drift Recap

M6-P6 kept audit-time records as snapshots and handled wording/data-shape drift
forward in implementation or verification:

- Logo subtitle: audit said `AI 智能化标注管理系统` or similar; actual code text is `AI 监督信号治理系统`. Handled in P6b1 commit `41096fa`.
- #22 mis-read: reviewer detail `提示 / 通过 / 拒绝` were legitimate ledger-card tags, not render residue. Removed at P6a closure and recorded in P6a docs.
- #32: code wrote `✓ 一致` in `0a606f8`, but CSS compressed the rendered Semi tag to `✓ ...`. Rendered visual was completed by nits commit `5c03dcf`.
- #38: P6a wording `AI 建议:看起来良好` was reasonable but ambiguous about label vs tag text. The duplication was completed by nits commit `5c03dcf`.

## Final State

M6-P6 closes as a frontend-only UI experience polish layer on top of the
M6-P5/P5.1 defense-readiness baseline. P6a locked the audit, P6b1 landed shared
primitives, P6b2 completed the six defense-path evidence surfaces, and the
remaining P1 candidates are optional future work.
