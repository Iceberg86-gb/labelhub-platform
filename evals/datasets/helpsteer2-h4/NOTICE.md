# HelpSteer2 attribution

`smoke-pool.jsonl` (and the locally built `pool.jsonl`) contain samples from **HelpSteer2** by NVIDIA,
licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/). `retrieval.jsonl` holds only
content hashes.

- Source: <https://huggingface.co/datasets/nvidia/HelpSteer2>, revision `990b2711a36180dd19d9c94b8627844866f8982a`, `train.jsonl.gz`
- Paper: Wang et al., *HelpSteer2: Open-source dataset for training top-performing reward models*, 2024.
- Changes: fields reduced to prompt/response, exact duplicates removed, and a derived `verdict`
  (`approved` / `rejected`, rule in `manifest.json` → `label_rule`). The pool is used as precedent memory
  for ADR-021 (H4) evals.
