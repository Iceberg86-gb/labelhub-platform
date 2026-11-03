# Eval run `76b0cd706ca35fdd`

- Golden set: `golden` (`5e0df257bb34`), 1038 items, truth pass/reject = 736/302
- Model: `chatgpt/gpt-6.1-terra@low`  ·  prompt `h1-single-call-v1`  ·  scoring `equal-weight-three-zone-v2` (pass ≥ 0.80, reject ≤ 0.20)

| Metric | Value |
|---|---|
| **Auto-pass error rate** (primary risk) | 8.93% |
| Auto-reject error rate | 53.14% |
| Manual-review rate | 33.14% |
| Agreement on decided items | 64.70% |
| Cohen's κ on decided items | 0.340892 |
| Format violation rate | 0.00% |
| Routed pass / manual / reject | 280 / 344 / 414 |
| Tokens in / out (total) | 1699922 / 355002 |
| Cost per item (USD) | n/a |
| Latency p50 / p95 (ms) | 8273 / 14282 |

## Validate-and-correct loop (`h2-validate-loop-v2`, max 3 steps, tool style, rules `review-output-v1`)

| Metric | Value |
|---|---|
| Invalid on first answer (any rule / correctable) | 63.20% / 54.14% |
| Flagged (judgement inconsistency, manual_review, not sent to the model) | 32.08% |
| Downgraded after the loop (still invalid, judgement changed or correction failed) | 0.00% |
| Correction success rate | 100.00% |
| Outcomes valid / corrected / flagged / downgraded / judgement changed / correction failed / format violation | 382 / 323 / 333 / 0 / 0 / 0 / 0 |
| Model calls (mean steps per item) | 1600 (1.5414) |
| First-answer error codes | suggestion_score_conflict 333, unknown_field 562 |
| Error codes left after the loop | suggestion_score_conflict 333 |
