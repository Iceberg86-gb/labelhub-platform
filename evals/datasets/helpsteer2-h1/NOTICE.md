# HelpSteer2 attribution

`smoke.jsonl` (and the locally built `golden.jsonl`) contain samples from **HelpSteer2** by NVIDIA,
licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/).

- Source: <https://huggingface.co/datasets/nvidia/HelpSteer2>, revision `990b2711a36180dd19d9c94b8627844866f8982a`, `validation.jsonl.gz`
- Paper: Wang et al., *HelpSteer2: Open-source dataset for training top-performing reward models*, 2024.
- Changes: random sample (seed in `manifest.json`), fields reduced to prompt/response plus the five attribute
  scores, and a derived `truth` label (rule in `manifest.json` → `label_rule`).
