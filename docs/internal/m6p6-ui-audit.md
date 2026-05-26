# M6-P6a UI Audit

Source: user-adjudicated review of
`docs/screenshots/m6p6-before-set/` on 2026-05-26.

This document is the locked input for M6-P6b. P6b may implement only the P0
items below, then selected P1 items if the P0 budget remains under the hard
cap. Any new issue discovered during implementation must be reported, not
silently fixed.

## Audit Position

M6-P6 is not an all-site redesign. It is a defense-path polish pass focused on:

- evidence visibility,
- obvious brokenness,
- readable hierarchy on the six demo paths.

The final P0 list deliberately excludes broader visual taste work and any
feature-level behavior change.

## Locked P0 Issue List

### Global

These are first-order dependencies. P6b must resolve them before page-level
polish so page work can reuse shared visual primitives.

| ID | Issue | Where seen | Action |
|----|-------|------------|--------|
| G-P0 #1 | Header logo subtitle text renders as broken pixel fragments ("AI 智能化标注管理系统" or similar) | Images 1, 3, 5, 9, 10 | Investigate root cause during P6b. Either fix render or remove subtitle. |
| G-P0 #2 | Hash values are truncated everywhere with no copy affordance | Schema versions, submission, export list, export diff, AI drawer | Create `<TruncatedHash>`: monospace font, fixed truncation, tooltip with full hash, copy icon. Replace all locked hash render sites. |
| G-P0 #3 | Role badge (`OWNER` / `LABELER` / `REVIEWER`) has poor readability | Authenticated pages | Create `<RoleBadge>` with distinct schemes: Owner purple, Labeler blue, Reviewer amber. Component-level only, no Semi theme token change. |
| G-P0 #4 | H1/H2/subtitle font sizes and colors are inconsistent | Owner schema, reviewer detail, export | Define a local typography scale: H1 28px, H2 22px, H3 18px, body 14px, caption 12px. Apply through existing app styles/CSS variables. |

### Owner Schema Designer

| ID | Issue | Action |
|----|-------|--------|
| #7 | Schema version history cards are visually weak even though this is a Highlight 1 evidence surface | Strengthen hierarchy: prominent current-version badge, `contentHash` via `<TruncatedHash>`, icon prefixes for field count and timestamp, and a vertical timeline visual between cards. No new data. |
| #8 | Drawer mask is too light; main-page text bleeds through | Increase mask opacity to approximately `rgba(0,0,0,0.5)`. |
| #10 | "展开 JSON" link looks awkwardly positioned | Visual cleanup only. Keep the action as a secondary action. Do not add compare/rollback pseudo-buttons. |

### Labeler Submission Detail

| ID | Issue | Action |
|----|-------|--------|
| #11 | Red "必填" tag appears on an already submitted answer and reads like an error | Hide it for submitted detail views, or replace with a neutral/success semantic if the current component requires a marker. |
| #12 | AI 检查记录 card is a Highlight 4 evidence surface but appears flat | Convert cost, latency, and completed status into three stat cards. Hashes use `<TruncatedHash>`. |
| #15 | Historical render marker is too weak for submissions bound to an older schema | Add a banner above the schema render area: "此 submission 按提交时的 Schema vN 渲染。当前 Schema 已更新到 vM,但历史答案不会改写。" Use existing fields only. |

### Reviewer Queue

| ID | Issue | Action |
|----|-------|--------|
| #17 | Table rows have no visible hover state | Add row hover background using existing Semi/table styling where possible. |
| #18 | Subtitle "从 append-only Quality Ledger 派生当前 Verdict" is too small for the Highlight 2 anchor text | Increase prominence and add an info icon with explanatory tooltip about ledger derivation. |

### Reviewer Detail

| ID | Issue | Action |
|----|-------|--------|
| ~~#22~~ | ~~"提示 / 通过 / 拒绝" residue~~ | **Removed after re-audit.** The text is legitimate ledger-card tags for Ledger #9/#10/#11, not a rendering bug. No P6b fix. |

R8 correction: the original audit mis-read the ledger-card tags as possible
render residue. The user caught the issue during adjudication and removed it
from P0. This is recorded to preserve audit accuracy, not to assign blame.

### Export List

| ID | Issue | Action |
|----|-------|--------|
| #26 | Trusted Export heading and subtitle have too little visual weight for a Highlight 3 entry point | Strengthen heading hierarchy and subtitle prominence. |
| #27 | Manifest Hash column is truncated and not copyable | Use `<TruncatedHash>`. |
| #28 | Multiple snapshots with the same `manifestHash` do not visually communicate reproducibility | Add a subtle "✓ same hash" indicator when existing snapshot data has repeated hashes. No new fields. |

### Export Diff Modal

| ID | Issue | Action |
|----|-------|--------|
| #31 | Success banner text is defense-grade narrative but too small | Promote banner text to hero-size treatment, approximately 16px+ and bold enough to read in demo. |
| #32 | Three-hash card shows "✓ ..." which looks like loading | Replace with "✓ 一致" or a `<TruncatedHash>` treatment, consistently across the three cards. |
| #33 | File-level SHA-256 table is the highlight moment but looks plain | Add a "10/10 一致" hero count badge, filled success tag style, and subtle green row tint for matching rows. |

### AI Drawer

| ID | Issue | Action |
|----|-------|--------|
| #35 | Idempotency-hit banner is the Signal 1A money-story surface but is visually underplayed | Strengthen banner styling: mid-blue background, larger info icon, bold key phrases. |
| #36 | Cost, latency, and completed are flat key/value rows | Convert to stat cards. For idempotency hit, include explicit copy: "本次未产生 cost(命中历史 0.0001 USD)" or equivalent based on current values. |
| #37 | Input hash and output hash are truncated and not copyable | Use `<TruncatedHash>`. |
| #38 | "建议: 看起来良好" label/tag arrangement is unclear | Rephrase as one coherent phrase, such as "AI 建议:看起来良好". |

### Owner Setup 3-CTA

| ID | Issue | Action |
|----|-------|--------|
| #40 | Third CTA "待前置" looks too similar to actionable "待处理" CTAs | Add lock icon to disabled/blocked CTA and visually distinguish blocked-by-prerequisite from actionable-pending. |

## Locked P1 Backlog

P1 work enters only after all P0 issues are done and P0 remains within the
900-line cap.

| ID | Issue |
|----|-------|
| #13 | Submission detail tag color semantics |
| #16 | Filter dropdown / refresh button style consistency |
| #21 | Reviewer ledger timeline restructure |
| #23 | Approve button success-green semantic |
| #25 | Verdict source provenance line emphasis |
| #29 / #30 | Export table column width / disabled-state polish |
| #34 | Diff modal width |
| #39 | AI drawer field feedback as cards |
| #42 / #44 | Owner setup spacing / empty-state copy |

## Out of Scope for P6

| ID | Reason |
|----|--------|
| #6 | Sidebar icon size is cosmetic and not an evidence surface |
| #19 | Schema name in queue is information-density nice-to-have |
| #20 | Pagination hiding when single page is an edge-case polish |
| #24 | Textarea collapse is behavior change and exceeds A-scope polish |
| #43 | Demo task name is demo data preparation, not UI polish |

## Screenshot D-口径

| Item | Reason |
|------|--------|
| `01-owner-schema-empty.png` not captured | No empty schema designer state in current dev data |
| `08-reviewer-queue-empty.png` not captured | Queue currently has submitted items |
| `11-ai-drawer-first-call.png` not captured | Existing submission already has AI call; drawer opens directly to idempotency hit |
| `13-ai-drawer-failure.png` not captured | Provider failure state was optional and not forced |
| `15-owner-setup-mid-step.png` not captured | Draft tasks lacked a real mid-step setup state |
| Auditor mis-read of reviewer-detail ledger tags as issue `#22` | Re-audit removed the item; ledger tags are legitimate content |

## P0 Budget Estimate

| Cluster | Estimated lines |
|---------|-----------------|
| `<TruncatedHash>` component + replacements | ~80 |
| Typography CSS variables | ~50 |
| `<RoleBadge>` component | ~40 |
| Header logo subtitle fix | ~10 |
| Schema version history visual rework | ~150 |
| Submission "必填" hide logic | ~15 |
| Submission AI stat cards | ~100 |
| Historical render banner | ~60 |
| Reviewer queue hover + subtitle | ~50 |
| Export heading + same-hash indicator | ~40 |
| Export diff modal hero text + file table | ~120 |
| AI drawer idempotency banner + stats | ~120 |
| Owner setup CTA lock state | ~30 |

Estimated P0 total: **~865 lines**.

Hard cap: **900 lines**. If P0 materially exceeds this estimate, P6b must stop
and ask for re-scope before entering P1.

## P6b Implementation Order

1. Global primitives: header/logo, role badge, typography.
2. Hash primitive: `<TruncatedHash>` and locked replacements.
3. Schema surfaces: version history and historical render banner.
4. AI provenance surfaces: submission AI card and AI drawer.
5. Export surfaces: snapshot list and diff modal.
6. Reviewer surfaces: queue hover and ledger-derivation subtitle.
7. Owner setup CTA lock state.

This order is part of the scope decision. P6b should not start with page-level
one-off styling before shared primitives are ready.
