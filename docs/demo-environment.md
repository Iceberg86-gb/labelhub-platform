# LabelHub 演示环境说明

> 整理日期:2026-06-26(当日实测公网首页 200 OK,健康探针返回 `{"status":"UP"}`)。
> 事实来源:`infra/docker-compose.prod.yml`、`infra/deploy/README.md`、`infra/nginx/labelhub.conf`、`docs/architecture/diagrams/deployment.md`、`humanpending.md`(closure 236/237/238)。

## 1. 访问入口一览

| 项目 | 值 |
|------|-----|
| 公网地址 | **http://120.26.182.61:8443/** |
| 健康探针(公开) | http://120.26.182.61:8443/api/actuator/health |
| API 前缀 | `/api/`(nginx 反代到后端容器 8080) |
| 协议形态 | HTTP + IP 直连(ICP 备案与 HTTPS 证书切换尚未完成,完成后切 443) |
| 服务器 | 阿里云单台 ECS,Ubuntu 24.04,Docker Compose 编排,部署根 `/opt/labelhub` |

对外演示、评审和真实用户访问时只使用公网地址。`http://127.0.0.1:5173` 是开发机上的 Vite 地址,只对启动前端 dev server 的那台机器有效,不能作为生产入口发给他人。

边缘安全策略(nginx 层):

- 仅 `/api/actuator/health` 公开,其余 actuator 端点与 `/api/internal/**`(agent 内部回调)在公网一律返回 **404**(刻意不是 403,避免探测者区分"被拦"与"不存在")。
- 上传上限 10MB(与后端 multipart 上限一致,超限在边缘返回 413)。
- 已启用 nosniff / X-Frame-Options DENY / CSP 等安全响应头;HSTS 待 TLS 切换后开启。

## 2. 演示账号

五种角色,职责正交:

| 角色 | 本地账号 | 说明 |
|------|---------|------|
| Owner(任务方) | `owner_demo` | 建任务、数据集、Schema、AI 预审、可信导出 |
| Labeler(标注员) | `labeler_demo` | 任务广场领取、作答、批量提交(注册默认角色) |
| Reviewer(初审员) | `reviewer_demo` | 全量初审、approve/reject、标记疑难 |
| Senior Reviewer(高审) | `senior_reviewer_demo` | 仲裁工作台(AI 升级/疑难/抽检 case) |
| Platform Admin | `platform_admin` | LLM Provider、用户角色治理、计量仪表盘、审计日志 |

**密码注意事项:**

- **本地环境**demo 账号密码均为 `demo1234`;本地 `platform_admin` 初始密码为 `dev-only-pa-password`。
- **公网演示环境的 demo 账号密码已轮换**(closure 236 生产事故账:seed 弱口令进入生产后已通过 SQL 改密焊死),登录页上硬编码的 `demo1234` 提示仅适用于本地环境。公网演示密码由项目 Owner 通过私密渠道发放,不写入仓库。
- `platform_admin` 由后端启动时的 `PlatformAdminSeeder` 自动引导创建,初始密码来自 `.env.prod` 的 `LABELHUB_PA_INITIAL_PASSWORD`,同样仅 Owner 持有。
- 新增演示账号请走注册页创建(注册固定产生 LABELER,再由 Platform Admin 授角),**不要手工 SQL INSERT**(避免 bcrypt hash 转义与 id 冲突)。

## 3. 环境拓扑(7 个容器)

```
浏览器 ── http://120.26.182.61:8443 ──▶ nginx (8443:80, 唯一公网暴露端口)
                                          ├── 静态站点 /opt/labelhub/infra/web-dist (web 构建产物)
                                          └── /api/ ──▶ api (Spring Boot, 1280m, Flyway 自动迁移)
                                                          │ 同事务写 outbox
                              agent (AI Worker, 768m) ──┘ 轮询 outbox / X-Internal-Token 回调 api
                              mysql 8 (1g) · redis 7 (192m, 预留) · minio + minio-init (512m, 导出产物)
```

- API 以 `SPRING_PROFILES_ACTIVE=prod` 启动,`ProductionSecretsGuard` 会拒绝使用仓库内开发默认的 `JWT_SECRET` / `LABELHUB_INTERNAL_TOKEN` 起服——密钥必须在 `.env.prod` 中显式提供。
- 数据持久化:`labelhub-mysql-data` / `labelhub-minio-data` 两个 named volume。
- 备份:每日 04:00 cron 执行 `infra/deploy/backup.sh` 到 `/opt/labelhub/backups`(产物目录 owner-only 权限);恢复走 `restore.sh`,已做过 27 表往返一致性演练。

## 4. 推荐演示动线(完整业务链)

按"AI 辅助 + 人类问责"主线走一遍五角色:

1. **Owner 登录** → 创建任务 → 上传数据集(JSON/JSONL,配额由题目数自动派生,无需手填)→ Schema Designer 设计并发布 v1(发布后版本不可变)→ 绑定数据集 → 发布任务。
2. **Labeler 登录** → 任务广场领取(FCFS,可自定义数量批量领取;领取瞬间绑定 Schema 版本)→ Renderer 作答(草稿 3–5s 自动保存)→ 本批次批量提交。
3. **Owner 触发 AI 预审**(前提:Platform Admin 已在平台内配置并激活 LLM Provider)→ 在提交详情查看 AI provenance:prompt 版本、模型、input/output hash、token 成本、延迟——AI 只产证据,不改裁决。
4. **Reviewer 登录** → 初审队列逐条/批量 approve、reject → 疑难条目"标记升级"。
5. **Senior Reviewer 登录** → 仲裁工作台处理 AI 升级 / 疑难标记 / 抽检 case → 裁决落 ledger(append-only,Verdict 实时派生)。
6. **Owner 创建 Trusted Export** → 选择训练格式(表格快照 / 对话微调 / 指令微调 / 偏好对比)→ 引导式字段绑定(含推荐与 0 行门控)→ 下载标注结果包 / 训练数据包 → 两次导出后演示快照 diff。
7. **Platform Admin 登录** → 成本/效率/人力计量仪表盘 → 审计日志查询与 CSV 导出。

演示数据现状:`infra/seed/demo-data.sql` 仅为占位,公网环境的演示数据是通过真实业务链路录入的(schema v1 发布、数据集 30 items、提交→初审→高审全链在生产实证过,见 closure 236)。

## 5. 运维与发布(仅项目 Owner 执行)

| 操作 | 命令/入口 |
|------|----------|
| 发布前端 | `scripts/deploy-web.sh`(本地构建 → rsync 到 `/opt/labelhub/infra/web-dist/`,带 `--delete`) |
| 发布后端 | `scripts/deploy-api.sh`(同步源码 → 远端 compose 构建重启 api/agent → 轮询 healthy → 鉴权探针自检) |
| SSH | `ssh -i ~/.ssh/labelhub-deploy.pem root@120.26.182.61` |
| 看容器状态 | `cd /opt/labelhub/infra && docker compose --env-file .env.prod -f docker-compose.prod.yml ps` |
| 公网快速自检 | `curl -f http://120.26.182.61:8443/api/actuator/health && curl -I http://120.26.182.61:8443/` |
| ECS 本机自检 | `curl -f http://127.0.0.1:8443/api/actuator/health && curl -I http://127.0.0.1:8443/` |

部署脚本为四阶段闭环,rsync exclude 列表必须锚定(如 `--exclude=/submission`)且两个脚本保持同步。当前 source sync 会排除 `.env*`、`target/`、根目录 `*.bundle`、本地配置目录、日志、coverage、本地数据目录、`submission/`、截图与设计资产,避免把本机产物或敏感配置带到生产。

## 6. 已知限制与挂账(演示前请知悉)

1. **未备案、无 HTTPS**:入口是 HTTP + IP + 非常规端口 8443,部分企业网络/浏览器策略可能拦截;备案与证书切换在挂账中。
2. **登录页 demo1234 提示仅适用于本地**;公网演示请使用 Owner 私下发放的轮换密码。
3. **AI 预审依赖 LLM Provider 配置**:演示前确认 Platform Admin 已录入并激活 provider(密钥经 `LABELHUB_LLM_PROVIDER_MASTER_KEY` 加密存库;master key 丢失则已存密钥不可读,需离线备份)。
4. **seed 无环境隔离**仍在挂账——不要把本地 seed 数据/账号再次带入生产。
5. 历史问题已闭合可放心演示:Trusted Export 快照物化失败(agent 数据源配置缺失,closure 237 已修复,导出链路生产三绿)、`crypto.randomUUID` 在 HTTP+IP 下瘫痪 Schema Designer(closure 238 已收编降级实现,公网手验通过)。

## 7. 本地演示备选方案

公网不可用时,本地起全套(详见根 README):

```bash
make doctor && make dev-up        # MySQL + Redis + MinIO
make dev-api                      # 后端(context path /api)
pnpm install && pnpm --filter @labelhub/web dev   # 前端 → http://127.0.0.1:5173
```

本地 demo 账号密码均为 `demo1234`。
