# A. AI Coding 过程记录(开发思路与过程文件)

> 整理日期:2026-06-10。本文档是 LabelHub 项目 AI Coding 全过程产物的导览与索引,所有事实来自仓库内既有文件,不做二次加工。

## 1. 协作模式总览

LabelHub 采用 **「人类 Owner 裁决 + AI 实施代理执行」** 的协作模式。AI 代理负责研究、估算、编码、验证与记录;人类 Owner 负责裁决关键分叉、亲手执行 closure/git 写操作、以浏览器实测和数据库 COUNT 做最终验收。整个协作受三份根目录契约约束:

| 契约文件 | 角色 |
|---------|------|
| [coderules.md](../coderules.md) | **META v2.1 宪章**(Principal Architect Charter):AI 代理的工程价值观与行为规则——R1 第一性原理分解、R5 执行即真相、R6 测试编码契约、R8 校准化汇报(executed/inspected/assumed 三级标注)、R9 反驳义务等 11 条规则,外加 Scoped Zero-Pause 层(ZPR0-ZPR5)与 LabelHub 项目特定约束(LH-1~LH-5) |
| [CODEX.md](../CODEX.md) | **开发协同契约**:工程实现规范——技术栈锁定、架构边界(api 是业务权威/agent 只回写证据/前端只发 command)、append-only 对象清单、幂等键统一格式、hash 规范化规则、四大亮点的不可妥协硬约束 |
| [humanpending.md](../humanpending.md) | **append-only 审计台账**(ADR-014 Scoped Zero-Pause 的落地):无法安全推断的业务规则不阻塞开发,记为 scoped 条目;每个合入批次留下交付与验证记录。当前已记录 **261 个闭环批次**,每条含交付内容、验证铁证、事故记录与封板锚点 |

## 2. 批次工作流(开发思路核心)

每个实施批次遵循固定的五阶段闭环,阶段产物全部落盘在 `docs/internal/`:

```
[Research 研究] ──── 跨域问题探索,产出决策素材(选项→推荐→理由)
      ↓
[Pre-Estimate 预估] ─ 缝隙分析、按集群分解估算、风险登记、
      ↓               User Adjudication Checklist ←── 人类 Owner 裁决
[Scope-Budget 范围预算] ─ 实施指导书:允许修改的文件清单 + 行数预算 +
      ↓                   禁区声明 + 停止条件(Stop Conditions)
[AI 编码实施] ─────── TDD red check(先让测试编译失败再实现)、
      ↓               500 行安全阀暂停复审、全过程记入 ai-coding-log.md
[Verification 验证] ── 估算 vs 实际对照、逐项核验命令输出、
      ↓               禁区未触碰确认(No-Touch)、截图证据
[Closure 封板] ────── 人类 Owner 亲手写入 humanpending.md,
                      封板锚点:HEAD commit / OpenAPI MD5 / 迁移数 / 台账序号
```

里程碑级别另有两类把关:**Audit**(批次健康度审计,检验决策是否全部留痕)与 **Smoke/Regression**(端到端全景体检,按 P0/P1/P2 分级记录缺陷与复现步骤)。

## 3. 过程文件全景(docs/internal/,共 124 个)

### 3.1 按类型统计

| 文件类型 | 数量 | 在流程中的角色 |
|---------|------|---------------|
| `*-scope-budget.md` | 48 | 实施指导书:允许文件清单、行数预算、设计锁定、风险登记、验证计划、停止条件 |
| `*-pre-estimate.md` | 37 | 入门评审关卡:Gap 分析、按集群(C1/C2/…)估算、对称性/风险登记、用户裁决清单 |
| `*-research.md` | 12 | 决策素材:每个决策点按"选项→推荐→理由"展开,含契约/迁移影响评估 |
| `*-verification.md` | 12 | 品质认证:commit map、估算 vs 实际、逐项测试输出、禁区确认、截图证据 |
| 其他(audit / smoke / 启动总览 / 封板快照 / 日志 / 修复计划) | 15 | 见 3.2 |
| **合计** | **124** | |

### 3.2 跨批次关键文件

| 文件 | 说明 |
|------|------|
| [docs/internal/ai-coding-log.md](../docs/internal/ai-coding-log.md) | **AI 工作日志**(541 行):自 2026-05-22 M0 起,每个批次记录 Prompt 原文、Red Check(TDD 失败先行)、Accepted(采纳的实现)、Manual Correction(人工更正及原因)、Verification(验证命令与结果)、截图证据路径。是"AI 写了什么、人改了什么、为什么改"的逐批流水 |
| [docs/internal/decision-log.md](../docs/internal/decision-log.md) | **决策日志**:全项目级决策史,按时间线记录每个阶段的技术决策与论证链(如:为何 `Schema` 改名 `LabelSchema`、为何表名用 `label_schemas`、事务写入顺序为何是 transition→audit→status) |
| [docs/internal/m3-startup-overview.md](../docs/internal/m3-startup-overview.md) | 批次启动总览样本:动笔前必须回答的 11 个元问题(provider 选择、同步/异步、权限模型等) |
| [docs/internal/coverage-checklist.md](../docs/internal/coverage-checklist.md) | 需求 vs 实施产物的双向追踪清单 |
| [docs/internal/LabelHub-pre-refactor-seal-snapshot.md](../docs/internal/LabelHub-pre-refactor-seal-snapshot.md) | UI 重构前的封板快照:OpenAPI MD5、迁移数、ADR 状态等不可被 UI 改动触碰的基线 |
| [docs/internal/m6p0-smoke-audit-report.md](../docs/internal/m6p0-smoke-audit-report.md) / [m6p5-final-regression-report.md](../docs/internal/m6p5-final-regression-report.md) | 端到端体检报告样本:按用户路径分轨审计,P0/P1/P2 缺陷分级与复现步骤 |

### 3.3 按里程碑分布

| 里程碑 | 文件数 | 主题 |
|--------|--------|------|
| M7-P5 | 14 | 离线草稿与多集群微调 |
| M7-P3b | 14 | 字段联动 DSL(linkage)多集群 |
| M7-P4a / P4b1 / P4b2 | 32 | 富文本、附件链、读契约等多轮集群 |
| M6-P5 / P6(b1/b2/c) / P7 | 20+ | 审核证据、UI 打磨三阶段、任务删除 |
| M7-P1 / P2 / P3a | 14 | 审计日志 UI、Formily 重构、答案校验 |
| Backend Batch A/B | 5 | 后端加固(安全/事务/死锁修复) |
| M3 与其他 | 其余 | AI 集成启动、专项研究与跨批次工具文档 |

研究类文档单列:Formily 选型([m7p2-formily-research.md](../docs/internal/m7p2-formily-research.md))、联动 DSL([m7p3b-linkage-dsl-research.md](../docs/internal/m7p3b-linkage-dsl-research.md))、离线草稿 localStorage vs IndexedDB([m7p5-offline-draft-research.md](../docs/internal/m7p5-offline-draft-research.md))、移动端适配([mobile-adaptation-research.md](../docs/internal/mobile-adaptation-research.md))、Designer/Renderer 课题合规([designer-renderer-compliance-research.md](../docs/internal/designer-renderer-compliance-research.md))、OpenAPI 工具链([openapi-setup-plan.md](../docs/internal/openapi-setup-plan.md))等。

## 4. 证据链:截图体系

[docs/screenshots/INDEX.md](../docs/screenshots/INDEX.md) 按 M1 Shell → M2 Schema → M3 AI Provenance → M4 Quality Ledger → M5 Export 的演进链组织 100+ 张截图,核心是**四大亮点的可复演证据链**:

1. **Schema 版本化**:v1 提交 → v2 发布 → 历史提交仍按 v1 渲染;
2. **Ledger 派生**:pending → approve 后 Verdict 变化且 ledger 只增不改;
3. **Export 可复现**:两次独立导出 hash 与字段映射全一致;
4. **AI Provenance**:真实 DeepSeek 调用 → 幂等复用 → 数据库四表证据。

另含防御性证据(跨身份 403、跨 Labeler 404、published 任务禁改数据集等)。批次级截图按 `m6p6-before-set` / `m6p6-after-set` 等成对目录存放,支持 before/after 对照。

## 5. 方法论纪律(从过程记录中提炼)

以下纪律在 ai-coding-log 与 humanpending 中反复出现,构成本项目 AI Coding 的特征:

1. **TDD Red Check 先行**:每个集群先写测试并确认编译/断言失败,再补实现——日志中每批都有 "Red check: … first failed at test compile" 记录。
2. **500 行安全阀**:单批实现超过 500 行即暂停等待人工复审(见 M2-P6b 记录)。
3. **最高证据标准**:单测通过 ≠ 功能正确;浏览器实测 + 数据库 COUNT 才算验收(README 关键取舍 10)。
4. **封板锚点**:每批 closure 记录 HEAD commit、OpenAPI MD5、迁移数、台账序号四锚,任何漂移可对账。
5. **事故如实入账**:生产事故(seed 弱口令入生产、bcrypt `$2y/$2a` 前缀、iCloud 同步产生重复 class 文件、Codex 越界自行 commit)全部写入台账并形成新纪律,不删不改。
6. **禁区机制**:scope-budget 明确"不得修改"的文件域(如前端批次禁触后端/契约/迁移),verification 中有 No-Touch Confirmation 逐项确认。
7. **裁决留痕**:所有需要人类决定的分叉(HTTP 422 vs 409、是否动前端)以 User Adjudication Checklist 显式上交,裁决结果写回文档。

## 6. 阅读路线建议

- **看协作机制** → coderules.md → CODEX.md →任选一组三件套(推荐 m7p2 的 pre-estimate / scope-budget / verification)
- **看完整流水** → docs/internal/ai-coding-log.md 从头读(M0 脚手架到 M2 全链最具代表性)
- **看质量闭环** → humanpending.md 的 closure 236(生产部署批,含五连缺陷与事故账)与 closure 258(Senior Reviewer 正交化重构)
- **看证据** → docs/screenshots/INDEX.md 的"90 秒四亮点演示路径"
