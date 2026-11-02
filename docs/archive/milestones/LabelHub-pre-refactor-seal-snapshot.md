# LabelHub 重构前完整封板状态总表（黄金快照）

> 用途：UI 重构前的后端/逻辑层封板基线。重构后逐项对照，确认前端重构未碰坏后端、契约、数据层、架构约束。
> 生成时点：六大核心需求 4.1–4.6 全封板 + 全局封板审计 PASS + P8 评分校准封板之后，UI 重构之前。
> 审计口径说明：标【grep 实证】= 审计师解压 zip 逐行 grep 核实；标【导出证据】= 基于 owner 提供的证据小包/git-log 导出文件核实；标【完整仓库复核】= 由 Codex 在完整本地仓库直接跑 git 验证（审计师因整包 251MB 传输受限无法直接跑，此档由 Codex 补足）。
> 本快照已由 Codex 在完整仓库复核校订（commit 链、HEAD 锚、后端无未提交 diff、ADR 计数），口径已据此升级。

---

## 一、当前封板锚（HEAD 状态）

| 项 | 值 | 口径 |
|---|---|---|
| HEAD commit | `8676f08`（P8 closure） | 完整仓库复核（Codex，HEAD ancestor 全部验证） |
| 分支 | `m6-engineering-hardening` | 完整仓库复核 |
| OpenAPI MD5（完整值） | `5102e4e97b9f842248aca651681b7b82` | 完整仓库复核 |
| migrations 数 | `21` | 完整仓库复核 |
| humanpending 计数 | `189` | 完整仓库复核 |
| 受保护后端/契约文件 | 无未提交 diff | 完整仓库复核 |
| 设计素材封存 | `docs/design-assets/` + 本快照同 commit 封存 | 完整仓库复核 |
| working tree | 本封存 commit 后应为 clean，再启动 UI 重构 | 完整仓库复核 |

> **working tree 口径说明**：上述后端/契约锚在 HEAD `8676f08` 时为干净封板状态。本快照采用方案 (a)：将 `docs/design-assets/`（设计系统素材 + tokens）与本快照一起 commit 封存。该封存 commit 之后，正式 UI 重构应从 clean working tree 开始。重构后对照时需区分：`docs/design-assets/` 属于已封存的素材备料；后续 `apps/web/` 改动才属于 UI 重构。
>
> 重构后回归基线：以上后端/契约 6 项是"未被重构碰过"的判据。重构只应改前端（`apps/web/`），OpenAPI MD5 / migrations / 后端 commit 不应因纯 UI 重构而变。若变，需核查是否动了契约或后端。

---

## 二、六大核心需求 × 相位 × 封板锚

| 需求 | 相位 | 内容 | 封板 commit | 封板时 OpenAPI MD5 | 评分 | 核法 |
|---|---|---|---|---|---|---|
| 4.1 任务管理 | P-E | 任务字段编辑、status DB CHECK、Excel 导入、available-only 批量编辑 | `8228f04` | `43db91c07c501ac41fa2fbf780e0dd9e`（完整值，审计时核） | 94 | grep 实证 |
| 4.2 标注页面动态搭建（★核心难点） | P-B + P-B-fix | JSON Schema 迁移、四物料、画布拖拽、x-reactions；C3 真拖拽修复 | `b09c0cf` → `ff3b949` | `cc4fafcf1505533df3a4642ff8bf6492` → `587bdda9132cc8c932099ceda4e704b5`（完整仓库复算补全） | 93 | grep 实证 |
| 4.3 标注员工作台 | P-D | 广场搜索筛选卡片、session 间导航、我的数据 senior-final verdict 统计 | `959cbbe` + 手验 `70916c0` | `7a53f0e1fb05950371fcb0af0d6650d1`（完整值，审计时核） | 95 | grep 实证 |
| 4.4 AI 自动预审（★核心难点） | P-A | submit→outbox→agent worker→AI evidence；function calling；三态；等权判分 | `5acbfb9` | `b808954de299551c7837278ed0e1ac52`（完整仓库复算补全） | 94 | grep 实证 |
| 4.5 多角色人工审核流转 | P-C | 打回闭环、reject reason、批量、两级 senior 复核、export approved-only 收口 | `df6ba9a` | `4a35f6083bc3be7c0e635fdbdd1668e7`（完整仓库复算补全） | 94 | grep 实证 |
| 4.6 多格式数据导出 | P-F + P-F-fix | CSV/Excel 业务表、字段映射、异步 export worker、下载入口；硬删除→归档改造 | `257a4bb` → `b029e9c` | `718f5438f596c3e597a51844967468d8` → `4467d7da2ac2b7f374edd02394bd70bd`（完整值，审计时核） | 95 | grep 实证（P-F-fix 归档 grep 实证；commit 链已由 Codex 完整仓库复核为 HEAD ancestor） |

> MD5 说明：当前后端封板 HEAD（P8）完整 MD5 为 `5102e4e97b9f842248aca651681b7b82`。表中历史相位 MD5 已由 Codex 在完整仓库使用 `git show <commit>:packages/contracts/openapi/labelhub.yaml | md5 -q` 复算补全。

---

## 三、commit 链连续性（全局封板审计 PASS）

链连续、无断裂、无游离、无假封板【完整仓库复核：Codex 验证全部 commit 为 HEAD ancestor；原 git-log-40.txt + 全局审计为导出证据档】：

```
P-B:      c8c8ca9 → e82edef → 2be23b2 → b09c0cf(closure)
P-B-fix:  2790195 → 48c2698 → dc96de8 → dc6075d → ff3b949
P-C:      1e37e3e → c974e3f → 8adb5f2 → 3a997b4 → 38fddb8 → eb293ca → adee570 → df6ba9a
P-D:      6c6f8b5 → a62e1d0 → 2342d35 → 959cbbe → 70916c0(手验seal)
P-E:      14ef2ca → 077185c → d7257d9 → be80261 → 7e6c71f → 8228f04(closure)
P-F:      34a2be1 → 94e62d5 → f81e7cc → 27319e3 → a797ef8 → 257a4bb(closure)
P-F-fix:  1c795e2(违规硬删) → ecc5dee(revert) → 60dd595(留痕) → 9829267(归档) → b029e9c(归档closure)
全局:     58eaba4(ADR清单回填)
P8:       34487dd → fd515ec → cb223af → 6786c5f → fe0c992 → 8676f08(closure)
```

> 诚实历史保留：`1c795e2` 违规硬删除快照（违反 ADR-004）真实留在链上，未 amend/reset 掩盖，紧跟 revert + 归档改造。违规可追溯，是健康的审计留痕。

---

## 四、ADR 治理清单（15 条，全局状态文档已回填）

| ADR | 主题 | 与重构的关系 |
|---|---|---|
| 001 | 模块化单体 | 架构形态，重构不动 |
| 002 | schema 版本不可变 + Field Stable ID | **重构红线**：不得碰 schema 不可变 |
| 003 | quality ledger append-only | **重构红线**：审核证据 append-only |
| 004 | export snapshot 不可变 + file hash | **重构红线**：导出快照不可变（已有违规拦截史） |
| 005 | AI evidence 非裁决、人工是问责门 | **重构红线**：审核台 UI 必须弱化 AI、强化人工 |
| 006 | function calling 强制 | 后端约束 |
| 007 | 先到先得分发 | 后端约束 |
| 008 | MySQL outbox + 轮询 worker | 后端约束 |
| 009 | 局部不可变（四类 evidence-critical 对象） | **重构红线** |
| 010 | Spring Boot + MyBatis-Plus | 技术栈 |
| 011 | 豆包默认 + OpenAI fallback | 后端约束 |
| 012 | contract-first OpenAPI | **重构红线**：前端类型源自生成，不手改契约 |
| 013 | FSD 前端 | **重构相关**：重构应沿用或显式调整 FSD 结构 |
| 014 | scoped zero pause | 流程约束 |
| 015 | OpenAPI 契约漂移控制 | **重构红线**：受保护端点不漂移 |

---

## 五、重构必须守住的架构红线（重构后逐条验）

1. **契约不漂移（ADR-012/015）**：纯 UI 重构不应改 OpenAPI。前端类型来自 generated schema.d.ts；重构若需新端点/字段，走契约 → 生成，不手改。重构后核 OpenAPI MD5 是否非预期变动。
2. **AI 非裁决（ADR-005）**：重构后审核台 UI 仍须 AI 证据弱化、人工裁决强化；final verdict 仍只由 senior reviewer 派生；标注员"通过"仍只认 senior-final。设计素材已贯彻此原则。
3. **不可变四对象（ADR-002/003/004/009）**：schema version / submission / quality ledger / export snapshot 不可变。重构是前端，不应触及，但若重构顺手"优化"了某个调用要警惕。export 快照尤其有违规前科。
4. **状态机语义**：task 状态（draft/published/paused/ended，DB CHECK）、审核流转（提交→初审→senior→通过/打回）、`returned_for_revision` vs 终态拒绝、`manual_review` 等——重构 UI 的状态展示必须对齐真实状态机（设计素材已区分非终态/终态）。
5. **批量编辑只改 available（P-E）/ approved-only 导出（P-C/P-F）**：这些承重守护在后端，重构不碰后端即守住；若重构触及相关调用需复核。

---

## 六、补证状态

### 已补证 / 已复核（Codex 完整仓库复核）

- **P-F-fix 与 P8 的 commit 链**：已由 Codex 在完整仓库复核——`9829267`、`b029e9c`、`34487dd` 到 `8676f08` 列出的全部链上 commit 均为当前 HEAD 的 ancestor。文档列出的 commit 链全部在 HEAD 历史内。原"待某次完整仓库复核"已闭环。
- HEAD `8676f08`、分支 `m6-engineering-hardening`、OpenAPI MD5 `5102e4e97b9f842248aca651681b7b82`、migrations `21`、humanpending `189`、ADR 编号文件 `15` 个——均经完整仓库核对，与本快照一致。
- P-B/P-A/P-C 等历史相位的截断 OpenAPI MD5 已由 Codex 使用完整仓库 `git show <commit>:packages/contracts/openapi/labelhub.yaml | md5 -q` 复算补全。
- 受保护后端/契约文件当前无未提交 diff（完整仓库复核）。

### 待补证（重构前后任意时点宜补）

1. **D-口径 UI 手验项**（重构会重做这些 UI，宜重构后一并手验）：
   - P-D：session 间上/下/跳导航、切 session 草稿不丢
   - P-E：批量编辑 skipped_locked 提示、Excel 上传交互
   - P-F：异步 export job 轮询（queued→running→succeeded）、下载拿到 CSV/Excel、字段映射配置
   - P-F-fix：归档按钮 + 已归档视图切换 + 归档快照仍可下载
   - P8：三区阈值配置 UI（passThreshold/rejectThreshold）

---

## 七、收尾增强状态（非核心需求）

| 项 | 状态 | 说明 |
|---|---|---|
| P6 移动端响应式 | 未做 | owner 明确等 UI 重构后再做 |
| P7 类型安全 | 未做 | 重构前后任意时点可做 |
| P8 AI 评分校准 | **已封板** | 任务级三区阈值、两路径统一、scoringRuleVersion=equal-weight-three-zone-v2 |

---

## 八、结论

六大核心需求 4.1–4.6 全部封板；commit 链连续无假封板；export 快照不可变守住（违规硬删除已 revert + 合规归档）；ADR 治理清单 15 条完整；humanpending 189 健康。**LabelHub 主体工程封板。** 此快照可作为 UI 重构的回归基线。
