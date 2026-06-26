# C. 源码仓库整理(前端 + 后端 + Agent,Monorepo)

> 整理日期:2026-06-10。结论先行:**本仓库本身就是单一 Monorepo**,前端、后端、AI Agent、API 契约、基础设施、文档同仓共版本,直接满足"前端 + 后端 + Agent,最好是 Monorepo"的提交要求,无需重组。

## 1. 仓库结构总览

```
LabelHub - Platform/                  ← Monorepo 根(pnpm workspace + Maven reactor 双工作区)
├── apps/
│   └── web/                          ← 前端:React 18 + TS + Vite + Semi + Formily(FSD 五层)
├── services/
│   ├── api/                          ← 后端:Spring Boot 模块化单体,13 个业务模块,业务权威边界
│   └── agent/                        ← AI Worker:独立 Spring 进程,轮询 outbox 执行 AI 预审/导出
├── packages/
│   └── contracts/                    ← API 契约源头:labelhub.yaml(89 operations)+ fixtures
├── infra/                            ← 本地/demo/生产三套 docker compose、nginx、备份脚本
├── scripts/                          ← dev-up / deploy-api / deploy-web / 契约保护检查
├── docs/                             ← 基线、ADR×15、架构图×4、状态机、截图证据(见 b 文档)
├── submission/                       ← 答辩提交材料索引(本目录)
├── humanpending.md / CODEX.md / coderules.md   ← AI Coding 三契约(见 a 文档)
├── pom.xml                           ← Maven reactor 根(api + agent 多模块)
├── pnpm-workspace.yaml / package.json ← pnpm workspace 根(web + contracts)
└── Makefile                          ← doctor / dev-up / dev-api / verify 统一入口
```

**Monorepo 的粘合点**:`packages/contracts/openapi/labelhub.yaml` 是前后端的唯一契约源头——前端每次 dev/build 自动 `gen:api` 生成 TS 类型;后端 Controller `implements` 由同一份 YAML 生成的 Java 接口,签名漂移在编译期失败。三个可运行单元 + 一份契约同仓同版本,这正是 Monorepo 的核心收益。

## 2. 规模与构成

| 单元 | 文件数 | 代码行数(约) | 说明 |
|------|--------|--------------|------|
| `apps/web/src` | 326 | 46,900 | 16 个 feature 切片;另有 69 个测试文件 / 338 条前端测试 |
| `services/api`(主代码) | 300 | 21,900 | 13 个业务模块包 + security/shared |
| `services/api`(测试) | 129 | 19,000 | 单测 + Testcontainers 集成测试 |
| `services/agent`(主代码 + 测试) | 42 | 2,600 | 双 worker + registry-first provider 解析 + 密钥脱敏 |
| `packages/contracts` | 6 | 5,200 | OpenAPI YAML + 校验/联动语料 fixtures |
| 数据库迁移 | 30 | — | Flyway,append-only,不改已发布迁移 |
| Git 提交史 | 497 commits | — | 其中 57 个 `--no-ff` 合并,对应 humanpending 批次闭环 |

## 3. 提交史本身是 AI Coding 证据

本仓库的 git 历史与过程文件互为锚点,**建议交付时保留完整提交史**:

- 每个批次以 `--no-ff` 合并,merge commit 与 `humanpending.md` 的 closure 序号对应;
- closure 记录里的封板锚点(HEAD commit / OpenAPI MD5 / 迁移数)可在历史中逐一对账;
- commit message 遵循 `fix:/feat:` 约定并带批次标记(如 `(P2-8)`),与 `docs/internal/` 的 scope-budget 文件可交叉引用。

## 4. 评审可执行的验证入口

公网演示环境:

```bash
open http://120.26.182.61:8443/
curl -f http://120.26.182.61:8443/api/actuator/health
```

本地复现环境:

```bash
make doctor                                # 环境自检(JDK17/Docker/pnpm)
make dev-up && make dev-api                # 基础设施 + 后端(/api)
pnpm install && pnpm --filter @labelhub/web dev   # 前端 → http://127.0.0.1:5173
make verify                                # 后端全量测试(自动准备测试库)
pnpm --filter @labelhub/web test           # 前端 338 条测试
bash scripts/check-protected-endpoints.sh  # ADR-015 契约保护检查
```

依赖锁定完整(`pnpm-lock.yaml` 入库、Maven 版本固定),评审机可复现构建。

## 5. 打包交付方案

### 方案一(推荐):git bundle,保留完整历史

```bash
git bundle create labelhub-platform.bundle --all
git bundle verify labelhub-platform.bundle   # 自检
# 评审侧还原:git clone labelhub-platform.bundle labelhub-platform
```

- 单文件、含全部 497 commits 与分支,AI Coding 过程证据链完整;
- 体积约同 `.git` 目录(~240MB,截图证据在历史中),U 盘/网盘均可承载。

### 方案二:干净源码归档(无历史,体积小)

```bash
git archive --format=zip -o labelhub-platform-src.zip HEAD
```

- `git archive` 只打包**已跟踪文件**,天然排除 `node_modules/`(326MB)、`target/`、`dist/`、`.env*` 实际密钥;
- 适合作为 bundle 的轻量补充(评审快速浏览源码用)。

两个方案都要求**先把未跟踪文件提交入库**,否则不会进包。

### 提交前检查清单

- [ ] 文档入库状态:根 README、`docs/api-inventory.md`、`docs/demo-environment.md`、`submission/` 均已纳入仓库跟踪;打包前仍需确认 `git status --short` 干净;
- [ ] 敏感信息核对(已验证现状):`.env` / `.env.prod` 均未被 git 跟踪,仓库内只有 `.env.example` 三份样例,无 `.pem` 入库;
- [ ] `docs/demo-environment.md` 只保留公网入口、账号名和密码发放规则;公网演示密码由项目 Owner 私密发放,不写入仓库;
- [ ] `scripts/deploy-*.sh` 与 `infra/deploy/README.md` 含服务器 IP 与 SSH key 路径——答辩范围可接受,公开发布前同样需评估;
- [ ] 打包后在干净目录做一次还原演练:`git clone <bundle>` → `make doctor` → `make verify`,确认评审侧可复现。

## 6. 提交材料对应表(官方六项要求)

| # | 提交要求 | 对应物 |
|---|---------|--------|
| 1 | 源码仓库(前端+后端+Agent,Monorepo) | 本仓库(bundle 或归档,见上) |
| 2 | README.md(架构、模块、启动指引、设计取舍) | [README.md](README.md)(根 README 副本,源头为 [../README.md](../README.md)) |
| 3 | 演示视频(5–10 分钟,三大角色完整链路) | [演示视频.mp4](演示视频.mp4)(约 78MB;录制动线参照 docs/workflows/demo-script.md) |
| 4 | 相关文档(架构图、关键技术点、Demo 截图) | [LabelHub-架构图与状态机.md](LabelHub-架构图与状态机.md);截图证据索引 [docs/screenshots/INDEX.md](../docs/screenshots/INDEX.md) |
| 4a | AI Coding 过程记录 | [a-ai-coding-process.md](a-ai-coding-process.md) + 仓库内过程文件原件 |
| 4b | 基础技术文档 | [b-technical-docs.md](b-technical-docs.md) + docs/ 原件 |
| 5 | 可访问的演示环境说明文档 | [docs/demo-environment.md](../docs/demo-environment.md)(http://120.26.182.61:8443/) |
| 6 | API 文档(Markdown 形式) | [docs/api-inventory.md](../docs/api-inventory.md) + [packages/contracts/openapi/labelhub.yaml](../packages/contracts/openapi/labelhub.yaml) |

> 注:`submission/` 含 78MB 视频,入库后 git bundle 体积相应增加(约 240MB → 320MB);若交付渠道对体积敏感,可在 bundle 之外单独传视频文件。`scripts/deploy-*.sh` 的 rsync 已锚定排除 `/submission`,并排除本地密钥、构建产物、根目录 `*.bundle` 与本地数据目录,不会被部署到服务器。
