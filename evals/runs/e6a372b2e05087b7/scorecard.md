# Eval run `e6a372b2e05087b7`

- Golden set: `golden` (`5e0df257bb34`), 1038 items, truth pass/reject = 736/302
- Model: `chatgpt/gpt-6.1-terra@low`  ·  prompt `h1-single-call-v1`  ·  scoring `equal-weight-three-zone-v2` (pass ≥ 0.80, reject ≤ 0.20)

| Metric | Value |
|---|---|
| **Auto-pass error rate** (primary risk) | 10.74% |
| Auto-reject error rate | 63.05% |
| Manual-review rate | 1.06% |
| Agreement on decided items | 53.55% |
| Cohen's κ on decided items | 0.196494 |
| Format violation rate | 0.00% |
| Routed pass / manual / reject | 326 / 11 / 701 |
| Tokens in / out (total) | 1842948 / 374482 |
| Cost per item (USD) | n/a |
| Latency p50 / p95 (ms) | 8588 / 13986 |

## Validate-and-correct loop (`h2-validate-loop-v1`, max 3 steps, tool style, rules `review-output-v1`)

| Metric | Value |
|---|---|
| Invalid on first answer | 63.20% |
| Still invalid after the loop (downgraded) | 0.00% |
| Correction success rate | 100.00% |
| Outcomes valid / corrected / downgraded / format violation | 382 / 656 / 0 / 0 |
| Model calls (mean steps per item) | 1694 (1.6320) |
| First-answer error codes | suggestion_score_conflict 333, unknown_field 562 |
| Error codes left after the loop | - |
