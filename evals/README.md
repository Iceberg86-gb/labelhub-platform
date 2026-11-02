# evals — LabelHub 离线评测（ADR-017 H1 / W1）

本目录提供"一次调用"AI 预审的离线评测，包括固定黄金集、录制 / 回放和可复现的成绩单；同时支持用 ChatGPT 账号登录，直接用订阅额度跑评测（[ADR-018](../docs/adr/ADR-018-chatgpt-sign-in-for-local-evals.md)）。

这里是本地工具，**不参与生产链路**。生产环境的 AI 预审仍然由 `services/agent` 负责（ADR-011）。

```bash
cd evals
python -m pip install -e '.[dev]'        # 可选：再加 '.[keyring]'，把加密密钥放进系统钥匙串
python -m pytest -q
```

## 1. 黄金集

```bash
python -m labelhub_evals golden build    # 下载固定版本的 HelpSteer2 validation，使用全部 1038 条
```

- 数据源按 Hugging Face revision 和 SHA-256 双重固定；默认使用全部 1038 条（736 pass / 302 reject），不抽样（humanpending #265）。`--size N --seed S` 只用于本地快速实验。取数方式和真值规则都写在 [`manifest.json`](datasets/helpsteer2-h1/manifest.json) 里。
- 重新构建得到的文件，如果与 manifest 不一致，命令会直接拒绝覆盖。
- 仓库里只提交 manifest 和 24 条 `smoke.jsonl`（供 CI 使用）。完整的 `golden.jsonl` 在本地按需重建。
- 真值规则 `hs2-pass-v1`：helpfulness ≥ 3 且 correctness ≥ 3 判为 pass，否则判为 reject。已由 owner 确认（humanpending #265）。

## 2. 跑评测

```bash
# 录制：缓存里没有的条目才真实调用模型，并写入 recordings/
python -m labelhub_evals run --provider chatgpt --mode record
python -m labelhub_evals run --provider openai-compatible --model gpt-4.1 --mode record   # 读 OPENAI_API_KEY
# 回放：只读缓存，零成本；缓存缺失直接报错
python -m labelhub_evals run --provider chatgpt --model gpt-5.5 --effort medium --mode replay --require-reproduced
```

| 模式 | 行为 |
|---|---|
| `record` | 先查缓存，未命中再调用模型并写入。结果写到 `runs/<run_id>/` |
| `replay` | 只查缓存，未命中报错。用于复现和 CI |
| `live` | 每次都调用模型，不写缓存。结果写到 `runs/_live/`（不提交） |

- **缓存键**：`(provider/model@effort, prompt_version, input_hash)`。其中 `input_hash` 按 conventions §7.2 先规范化再取 SHA-256。
- **`run_id`**：由黄金集哈希、模型身份、prompt 版本、打分规则和阈值共同推导，配置相同则 `run_id` 相同。`runs/<run_id>/` 是只追加的事实：内容逐字节一致时报 "reproduced"，不一致时直接报错，不会覆盖。
- **成绩单**（`scorecard.json` / `.md`）包含以下指标：
  - 自动通过区错误率（首要风险指标）
  - 自动打回区错误率
  - 转人工比例
  - 已判定条目上的一致率和 Cohen's κ
  - 格式违规率
  - token 用量、单条成本（需要传入 `--price-in/--price-out`）
  - p50/p95 延迟
- **格式违规的处理**：模型没有调用 `record_ai_review`，或者参数不合法时，这一条**确定性地转人工**，并且计入格式违规率。

`runs/1fef5379b2aec412/` 是 `fake` provider 在当前 smoke 集上的管线基线（`runs/d9a11ef8ee7f6210/` 对应已被替换的 500 条抽样版黄金集，按只追加原则保留），CI 用回放模式逐字节复现它。它只用来证明管线本身可复现，**不代表模型质量**。真实模型的 H1 基线需要 owner 登录之后录制，见下一节。

## 3. ChatGPT 登录与模型切换

```bash
python -m labelhub_evals chatgpt login            # 打开浏览器，在 OpenAI 页面上输入密码
python -m labelhub_evals chatgpt models           # 列出账号的模型目录，推荐的排在前面
python -m labelhub_evals chatgpt select --auto-latest      # 或者手动指定：select gpt-5.5 --effort medium
python -m labelhub_evals chatgpt test             # 用草稿配置真实请求一次
python -m labelhub_evals chatgpt apply            # 测试通过才会生效，并保存回滚点
python -m labelhub_evals chatgpt ask "你好"        # 用当前模型流式回答
python -m labelhub_evals chatgpt check-latest     # 只报告有没有更新的推荐模型，不会自动切换
python -m labelhub_evals chatgpt rollback | status | logout
```

整条链路：

```
login ─ PKCE(S256)+state+nonce ─▶ 浏览器 auth.openai.com ─▶ 127.0.0.1:1455/auth/callback（端口被占用时换成空闲端口）
      ─ 校验 state / client_id ─▶ 授权码换令牌 ─ 校验 nonce 与 chatgpt.tokens.use.direct scope ─▶ Fernet 加密，0600 权限落盘
models ─ GET api.openai.com/v1/models（visibility=list）─▶ select（草稿）─▶ test（真实请求）─▶ apply（校验账号 / 版本 / 草稿，压入回滚点）
run/ask ─ 令牌距离过期不足 5 分钟时先刷新；遇到 401 强制刷新，只重试一次 ─▶ POST /v1/responses（store=false，stream=true，SSE）
```

和 owner 参考实现（Electron 版）相比，这里有两处差别：

1. **按 OpenAI 官方的开源应用流程实现**：用动态注册的 client，回调地址用 `127.0.0.1`，接口走 `api.openai.com/v1`，不调用 `chatgpt.com/backend-api`。
2. **不做后台自动跟随最新模型**：`--auto-latest` 在执行的那一刻就把"最新"解析成一个具体的模型并固定下来，否则评测无法复现。

详见 ADR-018。

可以用的环境变量：

- `LABELHUB_EVALS_HOME`：令牌和配置的存放目录，默认是 `~/.config/labelhub-evals`
- `LABELHUB_EVALS_NO_KEYRING=1`：不使用系统钥匙串
- `LABELHUB_EVALS_AUTH_BASE` / `LABELHUB_EVALS_API_BASE`：仅测试时使用
