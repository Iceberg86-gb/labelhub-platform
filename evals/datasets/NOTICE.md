# 数据集署名

`helpsteer2-smoke.jsonl` 是从 **HelpSteer2** 抽取的 60 条子集（来自 validation split，按 accept / borderline / reject 分层抽样，每类 20 条，随机种子 20261102）。

- 来源：[nvidia/HelpSteer2](https://huggingface.co/datasets/nvidia/HelpSteer2)，revision `990b2711a36180dd19d9c94b8627844866f8982a`
- 许可：[CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/)
- 论文：Wang et al., *HelpSteer2: Open-source dataset for training top-performing reward models*, 2024
- 改动：保留了 `prompt`、`response` 和五个人工评分字段；新增了 `case_id`（内容哈希）和 `truth`（按 label mapping v1 推导）两个字段。

完整的 1038 条评测集不提交到仓库：运行时从上面固定的 revision 下载，并校验 sha256。
