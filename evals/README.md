# evals：AI 预审离线评测

[ADR-017](../docs/adr/ADR-017-annotation-agent-harness.md) 的 H1 阶段。这里回答一个问题：**AI 预审到底有多好？** 任何改变 AI 行为的改动（prompt、模型、阈值、agent 循环）都要先在这里出一份评测对比，然后才能合并。

## 测什么

**被测系统**：平台现在使用的"一次调用"AI 预审。评测发送的请求和生产环境完全一致：输入结构与 `AiReviewService.buildInput` 相同，提示词由 `PromptTemplate` 渲染，强制调用 `record_ai_review` 工具，`temperature=0`；拿到结果后，用三区阈值规则（`equal-weight-three-zone-v2`）决定这条提交的去向。

**为什么可以相信它测的就是生产系统**：工具 schema、提示词渲染结果、打分规则都放在 [`packages/contracts/ai/`](../packages/contracts/ai/) 下作为共享契约。Java 侧（`AiContractParityTest`、`OpenAiCompatibleAiReviewRuntimeClientTest`）和 Python 侧（`tests/test_contracts.py`）对照同一批文件做断言，任何一边改动了而另一边没有跟上，CI 就会失败。模型价格也直接读取 API 的 `application.yml`，不另外维护一份。

**数据**：[HelpSteer2](https://huggingface.co/datasets/nvidia/HelpSteer2)（CC-BY-4.0，固定在某个 revision，并校验 sha256）。每一行被当成一次标注提交：prompt 是数据集条目，response 是标注员的答案。人工给出的 0–4 分按 **label mapping v1** 转换成真值：

| 真值 | 规则 | validation 集中的条数 |
|---|---|---|
| accept | helpfulness ≥ 3 且 correctness ≥ 3 | 736 |
| reject | helpfulness ≤ 1 或 correctness ≤ 1 | 180 |
| borderline | 其余情况，交给人工判断就是正确结果 | 122 |

## 指标

| 指标 | 含义 |
|---|---|
| **自动通过错误率**（首要风险指标） | 被 AI 直接放行、但人类不会接受的比例 |
| 自动打回错误率 | 被 AI 打回、但其实可以接受的比例 |
| 人工复核率 | AI 分流之后，还剩多少工作量留给人工 |
| 明确样本的判定准确率 | 在 accept/reject 界限清晰的样本中，AI 自己做出的决定有多少是对的 |
| Cohen's κ | AI 的"通过"与人类的"接受"之间，扣除偶然因素之后的一致程度 |
| 最终分数的 AUROC | accept 与 reject 两类样本的分数能否被区分开。这个指标与阈值无关，是 H3 阈值校准的基础 |
| 格式违规率 / 回退 | 硬错误（一律转人工）和软警告（缺失的维度回退到用 confidence 代替） |
| 成本、延迟 p50/p95 | 按 token 计费：区分缓存命中与未命中，单价来自 API 配置 |

各项比例都附带 Wilson 95% 置信区间。60 条的冒烟集只用来检查流程能否跑通，**不能**拿来下结论；下结论要用完整的 1038 条评测集。

## 怎么跑

```bash
make eval-test                                   # lint + 单元测试，包括与 Java 的契约一致性
make eval-smoke                                  # 回放已提交的录制，离线、零成本、可复现

export LABELHUB_EVAL_API_KEY=...                 # 真实调用（默认 DeepSeek；其他 OpenAI 兼容服务需另设 LABELHUB_EVAL_BASE_URL）
make eval-record                                 # 60 条冒烟集：真实调用并录制
make eval-record EVAL_DATASET=helpsteer2-full    # 1038 条完整集，用 deepseek-v4-flash 跑一次不到 0.5 美元
```

也可以直接调用：`python -m labelhub_evals run --help`，可选 `--provider replay|record|live|fake`，并可以指定 `--model`、`--prompt`、`--pass-threshold`、`--reject-threshold`。

每次运行都会写入一个新目录 `runs/<时间>__<数据集>__<模型>__<配置哈希>/`，里面有 `manifest.json`（git commit、数据集哈希、prompt 哈希、工具契约哈希）、逐条结果 `results.jsonl`、汇总 `summary.json` 和报告 `report.md`。已有的运行目录不会被覆盖。

## 目录

```
labelhub_evals/   contracts（Python 侧契约） · sut（被测系统） · providers（真实调用/录制/回放/fake） · metrics · runner · report · cli
prompts/          业务提示词，按版本命名（helpsteer2-review.v1.md）
datasets/         60 条冒烟子集 + 数据集署名
recordings/       录制的真实响应，用于回放
tests/            每个测试对应一条契约
```

## 已经发现的问题

- **规则里配置的维度不会传给模型。** `PromptTemplate` 只包含业务提示词，`ai_review_rules.dimensions` 并没有出现在请求里。如果规则作者没有在提示词里写出维度名，模型返回的维度名就对不上，打分会悄悄地回退到用 `confidence` 代替。评测里的提示词明确写出了维度名，相当于一条写得好的规则。报告里的 `dimension_fallback` 警告会把这种情况统计出来。这个问题列为 H2 的修复项。
