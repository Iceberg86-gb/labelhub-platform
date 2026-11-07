# Eval run `93325c017dce9d66`

- Golden set: `golden` (`5e0df257bb34`), 1038 items, truth pass/reject = 736/302
- Model: `chatgpt/gpt-6.1-terra@low`  ·  prompt `h1-single-call-v1`  ·  scoring `equal-weight-three-zone-v2` (pass ≥ 0.80, reject ≤ 0.20)  ·  precedents `h4-precedents-v1` top 3 (retrieval `86d9655be9f9`)

| Metric | Value |
|---|---|
| **Auto-pass error rate** (primary risk) | 9.37% |
| Auto-reject error rate | 50.65% |
| Manual-review rate | 31.02% |
| Agreement on decided items | 68.44% |
| Cohen's κ on decided items | 0.386424 |
| Format violation rate | 0.00% |
| Routed pass / manual / reject | 331 / 322 / 385 |
| Tokens in / out (total) | 2933806 / 313487 |
| Cost per item (USD) | n/a |
| Latency p50 / p95 (ms) | 6869 / 16426 |

## Validate-and-correct loop (`h2-validate-loop-v2`, max 3 steps, tool style, rules `review-output-v1`)

| Metric | Value |
|---|---|
| Invalid on first answer (any rule / correctable) | 53.95% / 44.60% |
| Flagged (judgement inconsistency, manual_review, not sent to the model) | 30.35% |
| Downgraded after the loop (still invalid, judgement changed or correction failed) | 0.00% |
| Correction success rate | 100.00% |
| Outcomes valid / corrected / flagged / downgraded / judgement changed / correction failed / format violation | 478 / 245 / 315 / 0 / 0 / 0 / 0 |
| Model calls (mean steps per item) | 1501 (1.4461) |
| First-answer error codes | suggestion_score_conflict 315, unknown_field 463 |
| Error codes left after the loop | suggestion_score_conflict 315 |
