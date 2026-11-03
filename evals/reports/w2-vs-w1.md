# Eval comparison: `e6a372b2e05087b7` vs baseline `700607c95f112de6`

- Baseline: `chatgpt/gpt-6.1-terra@low` · prompt `h1-single-call-v1`
- Candidate: `chatgpt/gpt-6.1-terra@low` · prompt `h1-single-call-v1` · loop `h2-validate-loop-v1` max 3
- Golden set: `golden` (`5e0df257bb34`), 1038 items
- **Gate (auto-pass error rate must not rise): FAIL**

| Metric | Baseline | Candidate | Δ |
|---|---|---|---|
| Auto-pass error rate (gate: must not rise) | 8.93% | 10.74% | +1.81 pp |
| Auto-reject error rate | 53.14% | 63.05% | +9.91 pp |
| Manual-review rate | 33.14% | 1.06% | -32.08 pp |
| Agreement on decided items | 64.70% | 53.55% | -11.14 pp |
| Cohen's κ on decided items | 0.3409 | 0.1965 | -0.1444 |
| Format violation rate | 0.00% | 0.00% | +0.00 pp |
| Routed pass / manual / reject | 280 / 344 / 414 | 326 / 11 / 701 | |
| Tokens in / out | 949747 / 217749 | 1842948 / 374482 | |
| Latency p50 / p95 (ms) | 5414 / 10681 | 8588 / 13986 | |
| Invalid first answer → after loop | | 63.20% → 0.00% | |
| Correction success rate | | 100.00% | |
| Model calls | 1038 | 1694 | |
