# Eval run `d42a5aa0e2406ef5`

- Golden set: `helpsteer2-h1-smoke` (`3a68d2295657`), 24 items, truth pass/reject = 17/7
- Model: `fake/hash-heuristic-v1`  ·  prompt `h1-single-call-v1`  ·  scoring `equal-weight-three-zone-v2` (pass ≥ 0.80, reject ≤ 0.20)  ·  precedents `h4-precedents-v1` top 3 (retrieval `86d9655be9f9`)

| Metric | Value |
|---|---|
| **Auto-pass error rate** (primary risk) | 16.67% |
| Auto-reject error rate | n/a |
| Manual-review rate | 25.00% |
| Agreement on decided items | 83.33% |
| Cohen's κ on decided items | 0.000000 |
| Format violation rate | 0.00% |
| Routed pass / manual / reject | 18 / 6 / 0 |
| Tokens in / out (total) | 15061 / 1440 |
| Cost per item (USD) | n/a |
| Latency p50 / p95 (ms) | 869 / 1373 |

## Validate-and-correct loop (`h2-validate-loop-v2`, max 3 steps, tool style, rules `review-output-v1`)

| Metric | Value |
|---|---|
| Invalid on first answer (any rule / correctable) | 0.00% / 0.00% |
| Flagged (judgement inconsistency, manual_review, not sent to the model) | 0.00% |
| Downgraded after the loop (still invalid, judgement changed or correction failed) | 0.00% |
| Correction success rate | n/a |
| Outcomes valid / corrected / flagged / downgraded / judgement changed / correction failed / format violation | 24 / 0 / 0 / 0 / 0 / 0 / 0 |
| Model calls (mean steps per item) | 24 (1.0000) |
| First-answer error codes | - |
| Error codes left after the loop | - |
