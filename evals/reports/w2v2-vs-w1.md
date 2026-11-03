# Eval comparison: `76b0cd706ca35fdd` vs baseline `700607c95f112de6`

- Baseline: `chatgpt/gpt-6.1-terra@low` · prompt `h1-single-call-v1`
- Candidate: `chatgpt/gpt-6.1-terra@low` · prompt `h1-single-call-v1` · loop `h2-validate-loop-v2` max 3
- Golden set: `golden` (`5e0df257bb34`), 1038 items
- **Gate (auto-pass error rate must not rise): PASS**

| Metric | Baseline | Candidate | Δ |
|---|---|---|---|
| Auto-pass error rate (gate: must not rise) | 8.93% | 8.93% | +0.00 pp |
| Auto-reject error rate | 53.14% | 53.14% | +0.00 pp |
| Manual-review rate | 33.14% | 33.14% | +0.00 pp |
| Agreement on decided items | 64.70% | 64.70% | +0.00 pp |
| Cohen's κ on decided items | 0.3409 | 0.3409 | +0.0000 |
| Format violation rate | 0.00% | 0.00% | +0.00 pp |
| Routed pass / manual / reject | 280 / 344 / 414 | 280 / 344 / 414 | |
| Tokens in / out | 949747 / 217749 | 1699922 / 355002 | |
| Latency p50 / p95 (ms) | 5414 / 10681 | 8273 / 14282 | |
| Invalid first answer → after loop | | 63.20% → 0.00% | |
| Flagged (manual_review, not sent to the model) | | 32.08% | |
| Correction success rate | | 100.00% | |
| Model calls | 1038 | 1600 | |
