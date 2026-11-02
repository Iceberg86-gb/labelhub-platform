# LabelHub 移动端适配 RESEARCH

生成日期: 2026-06-06

范围约束: 本文只做仓库只读调研与官方文档核对,不写实现方案,不估 LOC,不拆 cluster。所有本地结论均附文件路径证据;无证据处只标记为待证或风险,不下确定性结论。

## 取证结论

1. Web 前端使用 Semi Design `@douyinfe/semi-ui` / `@douyinfe/semi-icons` `^2.85.0`, React 18.3.1, Formily 2.3.2。证据: `apps/web/package.json:18-21`, `apps/web/package.json:25-26`。
2. Semi 官方定位是 React UI desktop component library; 本项目未引入 Semi `Row` / `Col` / `Grid` 组件,移动端适配主要依赖全局 CSS、页面自有 grid/flex 与少量组件显式属性。证据: `apps/web/src/app/styles.css:1771`, `apps/web/src/app/styles.css:3805`, `apps/web/src/shared/ui/AppLayout.tsx:112-119`; `rg "import \\{[^}]*\\b(Row|Col|Grid)\\b|<Row\\b|<Col\\b" apps/web/src` 无命中。
3. 全局布局壳已有 800px 移动断点: `AppLayout` 监听 `matchMedia('(max-width: 800px)')`,窄屏将侧边栏从折叠栏切为抽屉; `index.html` 已配置 viewport meta。证据: `apps/web/src/shared/ui/AppLayout.tsx:81-83`, `apps/web/src/shared/ui/AppLayout.tsx:112-136`, `apps/web/src/app/styles.css:3805-3875`, `apps/web/index.html:5`。
4. 作答页不是固定总宽: 当前为双列 CSS grid,900px 断点后单列; `SchemaFormilyRenderer` 自身没有响应式断点,但虚拟滚动分支有 `initialRect: { width: 800, height: 720 }` 和 `maxHeight: 720`。证据: `apps/web/src/pages/labeler/LabelerSessionPage.tsx:330-356`, `apps/web/src/app/styles.css:4199-4204`, `apps/web/src/app/styles.css:6133-6145`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:88-104`。
5. Reviewer 队列是 8 列 Semi Table,未在 `<Table>` 上显式设置 `scroll`; 详情页不是 Table,而是双栏 workbench + `ReviewerAnswerSummary`。证据: `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:46-93`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:235-244`, `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:131-164`, `apps/web/src/pages/reviewer/ReviewerAnswerSummary.tsx:22-46`。
6. CSS 方案集中在 `apps/web/src/app/styles.css` 与设计 token: `tokens.css` 被 app 入口一次性导入,本轮 grep 到 13 个 `@media` 均在 `styles.css`,未发现 CSS module / scss / less 文件。证据: `apps/web/src/app/main.tsx:4-8`, `docs/design-assets/tokens/tokens.css:1-119`, `apps/web/src/app/styles.css:1771`, `apps/web/src/app/styles.css:6353`; `rg --files apps/web/src | rg '\\.(module\\.css|scss|sass|less)$'` 无命中。
7. M7-P2 的 `05-formily-designer-preview-3viewports.png` 不是 live 3 viewport 页面截图,而是生成的 1280x1280 closure evidence card; 当时 1440 / 1280 / 1024 live sanity 因 seeded data 缺口被记录为 watch。证据: `docs/screenshots/m7p2-after-set/INDEX.md:10-15`, `docs/screenshots/m7p2-after-set/INDEX.md:25`, `docs/internal/m7p2-verification.md:302-317`。

## Semi Design 版本与组件清单

### 官方文档取证边界

| 官方证据 | 可采信结论 | URL |
|---|---|---|
| Semi Overview | Semi 是 React UI desktop component library,不能据此推断已有移动端自动适配。 | https://semi.design/en-US/start/overview |
| Grid | Semi Grid 支持 `xs` / `sm` / `md` / `lg` / `xl` / `xxl` 响应式栅格与 responsive gutter; 本项目未引入 Row/Col/Grid。 | https://semi.design/en-US/basic/grid |
| Table | Table 横向/纵向滚动依赖 `scroll.x` / `scroll.y`; 固定列示例要求设置 `scroll.x`。 | https://semi.design/en-US/show/table |
| Space | 横向 Space 只有显式 `wrap` 时才自动换行,默认 `wrap=false`。 | https://semi.design/en-US/basic/space |
| Modal | Modal 有 `fullScreen`, `size`, `width` 等配置; 这些是显式属性,不是项目已自动启用的移动端行为。 | https://semi.design/en-US/show/modal |
| SideSheet | SideSheet 支持 `placement`, `size`, `width`, `disableScroll`; 这些是显式属性,不是项目已自动启用的移动端行为。 | https://semi.design/en-US/show/sidesheet |

### Semi UI import 统计

口径: grep `apps/web/src` 下生产 `.ts/.tsx`,排除 `*.test.*`, `__tests__`, `__typechecks__`;按 `@douyinfe/semi-ui` 命名导入次数排序。

| 组件 | 次数 | 本地证据 | 小屏已知行为 |
|---|---:|---|---|
| Typography | 66 | `apps/web/src/shared/ui/AppLayout.tsx:1`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:1` | 未采信官方自动小屏行为; 受容器 CSS 控制。 |
| Button | 49 | `apps/web/src/pages/owner/OwnerTasksListPage.tsx:1`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:1` | 未采信官方自动小屏行为; 按本地 toolbar / Space / flex 处理。 |
| Tag | 33 | `apps/web/src/entities/task/TaskStatusBadge.tsx:1`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:1` | 未采信官方自动小屏行为; 长内容依赖本地 CSS。 |
| Toast | 26 | `apps/web/src/pages/owner/OwnerTasksListPage.tsx:1`, `apps/web/src/features/export/useDownloadExportFileMutation.ts:2` | 反馈浮层,本轮不作为布局适配能力。 |
| Empty | 26 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:1`, `apps/web/src/pages/platform/PlatformCostDashboardPage.tsx:1` | 未采信官方自动小屏行为; 空状态尺寸看本地 `.task-state-panel`。 |
| Spin | 24 | `apps/web/src/pages/labeler/LabelerSessionPage.tsx:1`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:1` | 未采信官方自动小屏行为。 |
| Space | 21 | `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:110`, `apps/web/src/features/task/transition-task/TransitionButtons.tsx:18` | 官方可证: 横向 Space 需显式 `wrap` 才自动换行; 本地部分有 `wrap`,部分没有。 |
| Input | 21 | `apps/web/src/pages/owner/OwnerSchemasListPage.tsx:125-130`, `apps/web/src/pages/owner/OwnerAuditLogsPage.tsx:156-157` | 未采信官方自动小屏行为; 本地有固定 `style.width` 风险点。 |
| Card | 14 | `apps/web/src/pages/owner/OwnerSubmissionPage.tsx:105`, `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:132` | 未采信官方自动小屏行为; 由页面 grid/flex 决定。 |
| Table | 13 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:235-244`, `apps/web/src/pages/owner/OwnerTasksListPage.tsx:248`; `rg "scroll=" apps/web/src -g '*.tsx'` 无命中 | 官方可证: 横向/纵向滚动依赖 `scroll.x/y`; 本轮 grep 未发现 Table 显式设置 `scroll`。 |
| Tooltip | 11 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:146-148`, `apps/web/src/pages/owner/OwnerTasksListPage.tsx:185-193` | 浮层类组件,本轮不采信自动移动端布局。 |
| Select | 11 | `apps/web/src/pages/labeler/LabelerSessionPage.tsx:304-309`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:153-170` | 未采信官方自动小屏行为; 本地有 `minWidth` / class 宽度风险点。 |
| TextArea | 10 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:267-271`, `apps/web/src/pages/owner/OwnerLlmSettingsPage.tsx:634` | 未采信官方自动小屏行为; 本地 autosize 只影响高度。 |
| Switch | 9 | `apps/web/src/pages/owner/OwnerLlmSettingsPage.tsx:748-762`, `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:1` | 未采信官方自动小屏行为; 在设置行里受 grid/flex 控制。 |
| Modal | 9 | `apps/web/src/pages/owner/OwnerAuditLogsPage.tsx:186`, `apps/web/src/features/dataset/DatasetUploadSection.tsx:270-283` | 官方可证: 可用 `fullScreen` / `size` / `width`; 本地多处使用固定 `width`。 |
| Pagination | 7 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:245-253`, `apps/web/src/pages/owner/OwnerTasksListPage.tsx:249-255` | 未采信官方自动小屏行为; 受 `.task-pagination` 与表格容器控制。 |
| Popconfirm | 5 | `apps/web/src/pages/owner/OwnerTasksListPage.tsx:121-138`, `apps/web/src/pages/admin/UserManagementPage.tsx:107-122` | 浮层类组件,本轮不采信自动移动端布局。 |
| InputNumber | 5 | `apps/web/src/features/labeling/formily/components/LabelHubNumberField.tsx:1`, `apps/web/src/features/schema-design/field-editors/NumberFieldEditor.tsx:1` | 未采信官方自动小屏行为; 受表单容器宽度控制。 |
| Form | 5 | `apps/web/src/pages/login/LoginPage.tsx:1`, `apps/web/src/features/task/create-task/CreateTaskModal.tsx:1` | 未采信官方自动小屏行为; 本地表单多在 Modal 或 login shell 内。 |
| Banner | 4 | `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:236-242`, `apps/web/src/features/export/ExportSnapshotDiffModal.tsx:1` | 未采信官方自动小屏行为; 受父容器控制。 |
| SideSheet | 3 | `apps/web/src/features/ai/AiReviewDrawer.tsx:26`, `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx:69-72` | 官方可证: 支持 placement/size/width/disableScroll; 本地使用固定 `width={640}`。 |
| DatePicker | 2 | `apps/web/src/pages/owner/OwnerAuditLogsPage.tsx:158`, `apps/web/src/features/labeling/formily/components/LabelHubDateField.tsx:1` | 未采信官方自动小屏行为; 审计筛选在 auto-fit grid 内。 |
| Upload | 1 | `apps/web/src/features/dataset/DatasetUploadSection.tsx:1` | 未采信官方自动小屏行为; 数据集上传区由本地 CSS 控制。 |
| Timeline | 1 | `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx:1`, `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx:229-234` | 未采信官方自动小屏行为。 |
| RadioGroup | 1 | `apps/web/src/features/dataset/DatasetUploadSection.tsx:1` | 未采信官方自动小屏行为。 |
| Popover | 1 | `apps/web/src/features/schema-design/AddFieldButton.tsx:1` | 浮层类组件,本轮不采信自动移动端布局。 |
| Checkbox | 1 | `apps/web/src/features/export/TrustedExportCard.tsx:1` | 未采信官方自动小屏行为。 |

补充: `@douyinfe/semi-icons` 同版本 `^2.85.0`;生产代码 import 前十为 `IconRefresh` 17、`IconDelete` 8、`IconPlus` 6、`IconPlay` 4、`IconEdit` 4、`IconArrowLeft` 4、`IconUpload` 3、`IconTickCircle` 3、`IconSearch` 3、`IconInfoCircle` 3。证据: `apps/web/package.json:18`; import grep 范围同上。

## SchemaFormilyRenderer 与作答页布局

| 结论 | 证据 |
|---|---|
| `SchemaFormilyRenderer` 外层是 `.schema-renderer schema-renderer--formily`,无组件内响应式断点。 | `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:54-63`, `apps/web/src/app/styles.css:3946-3950` |
| 大表单超过 50 个顶层字段时启用虚拟滚动。虚拟滚动有 `initialRect: { width: 800, height: 720 }`,`estimateSize: 112`,`maxHeight: 720`。 | `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:13`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:67-68`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:88-104` |
| Labeler 作答页使用上下文 rail + answer panel 两列布局,不是固定页面宽度。 | `apps/web/src/pages/labeler/LabelerSessionPage.tsx:330-356`, `apps/web/src/app/styles.css:4199-4212` |
| 900px 以下作答页 header/action/layout 进入单列,但 session navigation 内的 `Select` 有 `minWidth: 220`,答案 Card body padding 为 24。 | `apps/web/src/pages/labeler/LabelerSessionPage.tsx:304-309`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:344-346`, `apps/web/src/app/styles.css:6133-6145` |
| M7-P2 的三视口标准是 1440 / 1280 / 1024,但当时 live browser sanity 被 seeded-data 缺口阻断;截图集为 closure evidence cards。 | `docs/internal/m7p2-scope-budget.md:220-224`, `docs/internal/m7p2-scope-budget.md:274`, `docs/internal/m7p2-verification.md:302-317`, `docs/screenshots/m7p2-after-set/INDEX.md:10-15` |

## Reviewer 队列与详情页

| 页面 | 当前结构 | 小屏改 List 卡片的改造面初判 | 证据 |
|---|---|---|---|
| ReviewerQueuePage | 8 列 Table: `Submission`, `任务`, `Schema`, `提交时间`, `AI 预审`, `Verdict`, `层级`, `操作`;还有 rowSelection、分页、筛选、批量打回 Modal。 | 涉及 `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx`, `apps/web/src/app/styles.css`, `apps/web/src/pages/reviewer/ReviewerPages.test.tsx`。风险集中在行选择、批量操作、分页、筛选状态、开始审核入口是否在卡片模式仍可达。 | `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:46-93`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:151-179`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:192-244`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:258-273`, `apps/web/src/pages/reviewer/ReviewerPages.test.tsx:221-276` |
| ReviewerSubmissionPage | 不是 Table; 主体是 `reviewer-workbench-grid`,左侧 `ReviewerAnswerSummary`,右侧人工决策、AI 字段发现、ledger。 | 涉及 `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx`, `apps/web/src/pages/reviewer/ReviewerAnswerSummary.tsx`, `apps/web/src/app/styles.css`, `apps/web/src/pages/reviewer/ReviewerPages.test.tsx`。风险集中在双栏折叠、证据/裁决区域顺序、JSON details 与 ledger 时间线可读性。 | `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:131-164`, `apps/web/src/pages/reviewer/ReviewerAnswerSummary.tsx:22-46`, `apps/web/src/app/styles.css:4506-4510`, `apps/web/src/app/styles.css:4597-4644`, `apps/web/src/app/styles.css:6163-6204` |

补充事实: `.task-table-surface` 自带 `overflow-x: auto`,所以当前 Table 窄屏底线更接近横向滚动,不是 List 卡片。证据: `apps/web/src/app/styles.css:2263-2269`, `apps/web/src/app/styles.css:4437-4439`。

## 全局布局壳与 viewport

| 结论 | 证据 |
|---|---|
| viewport meta 已配置。 | `apps/web/index.html:5` |
| AppLayout 存储 `isCompactViewport`, `isSidebarCollapsed`, `isSidebarDrawerOpen` 三类状态。 | `apps/web/src/shared/ui/AppLayout.tsx:79-84` |
| 800px 以下用 `matchMedia('(max-width: 800px)')` 进入 compact viewport; toggle 行为从 collapse 改为 drawer open。 | `apps/web/src/shared/ui/AppLayout.tsx:112-128` |
| compact viewport 下点击 nav item 会关闭侧栏抽屉。 | `apps/web/src/shared/ui/AppLayout.tsx:130-136`, `apps/web/src/shared/ui/AppLayout.tsx:236-241` |
| CSS 800px 断点将 `.app-body` 改为单列,侧边栏固定定位并默认 translateX 隐藏,`.app-shell--sidebar-open` 时滑入。 | `apps/web/src/app/styles.css:3805-3835` |
| 960px 断点隐藏 topbar context,800px 断点压缩 app content padding。 | `apps/web/src/app/styles.css:1771-1778`, `apps/web/src/app/styles.css:3873-3875` |

## CSS 方案盘点

| 项 | 结论 | 证据 |
|---|---|---|
| token 引入 | App 入口先导入 Semi base CSS,再导入 `docs/design-assets/tokens/tokens.css`,最后导入 `styles.css`。 | `apps/web/src/app/main.tsx:4-8` |
| token 内容 | `tokens.css` 定义颜色、间距、圆角、阴影、字体等 `:root` 变量。 | `docs/design-assets/tokens/tokens.css:1-119` |
| token 使用 | `styles.css` 中 `var(--...)` 命中 1314 次; `apps/web/src` 下 `.ts/.tsx/.css` 合计 1347 次。 | `apps/web/src/app/styles.css:45-78`, `apps/web/src/app/styles.css:312-329`; grep 命令: `rg -o "var\\(--" ...` |
| 媒体查询 | `apps/web/src` 下 CSS 媒体查询共 13 个,全部在 `apps/web/src/app/styles.css`。断点包括 1280、1200、1024、960、920、900、800、760、560。 | `apps/web/src/app/styles.css:1771`, `apps/web/src/app/styles.css:1825`, `apps/web/src/app/styles.css:1853`, `apps/web/src/app/styles.css:2655`, `apps/web/src/app/styles.css:3059`, `apps/web/src/app/styles.css:3077`, `apps/web/src/app/styles.css:3805`, `apps/web/src/app/styles.css:3939`, `apps/web/src/app/styles.css:5682`, `apps/web/src/app/styles.css:6123`, `apps/web/src/app/styles.css:6133`, `apps/web/src/app/styles.css:6306`, `apps/web/src/app/styles.css:6353` |
| CSS 模块/预处理 | 本轮未发现 CSS module / scss / sass / less 文件。 | 命令: `rg --files apps/web/src | rg '\\.(module\\.css|scss|sass|less)$'` 无命中 |
| 表格底线 | 通用 `.task-table-surface` 提供 `overflow-x: auto`。 | `apps/web/src/app/styles.css:2263-2269` |
| 既有响应式样式 | 已存在针对 login、designer、app shell、labeler session、reviewer、trusted export、LLM 设置的断点。 | `apps/web/src/app/styles.css:1771-1859`, `apps/web/src/app/styles.css:3059-3105`, `apps/web/src/app/styles.css:3805-3875`, `apps/web/src/app/styles.css:6133-6273` |

## Owner / PA 页面清单与不残废风险点

PA 口径: router 中 `PLATFORM_ADMIN` 保护的 `/platform/*` 路由,其中用户与角色页面组件位于 `apps/web/src/pages/admin/`。证据: `apps/web/src/app/router.tsx:152-229`。

| 角色 | 页面 / 组件 | 当前承载 | 不残废最小风险点 | 证据 |
|---|---|---|---|---|
| Owner | `/owner/tasks` / `OwnerTasksListPage` | 任务概览 + toolbar + 6 列 Table + pagination。 | 表格横滚底线、toolbar 换行、操作按钮与 Popconfirm 可达性。 | `apps/web/src/app/router.tsx:102-109`, `apps/web/src/pages/owner/OwnerTasksListPage.tsx:77-143`, `apps/web/src/pages/owner/OwnerTasksListPage.tsx:202-255` |
| Owner | `/owner/tasks/:taskId` / `OwnerTaskDetailPage` | detail grid、状态操作、数据集上传、可信导出、提交记录。 | 详情卡片顺序、数据集/提交/导出内部表格与 builder 横向溢出。 | `apps/web/src/app/router.tsx:112-119`, `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx:170-256`, `apps/web/src/features/dataset/DatasetUploadSection.tsx:269-283`, `apps/web/src/features/export/TrustedExportCard.tsx:323-390` |
| Owner | `/owner/tasks/:taskId/submissions/:submissionId` / `OwnerSubmissionPage` | 两栏 `owner-submission-grid`: read-only Formily + AI provenance。 | 360px rail 折叠、Formily read-only 内容与 AI provenance 顺序。 | `apps/web/src/app/router.tsx:122-129`, `apps/web/src/pages/owner/OwnerSubmissionPage.tsx:104-116`, `apps/web/src/app/styles.css:4324-4329`, `apps/web/src/app/styles.css:6155-6157` |
| Owner | `/owner/schemas` / `OwnerSchemasListPage` | 搜索 toolbar + 4 列 Table。 | 搜索输入固定 260px、表格横滚或卡片化底线。 | `apps/web/src/app/router.tsx:132-139`, `apps/web/src/pages/owner/OwnerSchemasListPage.tsx:64-101`, `apps/web/src/pages/owner/OwnerSchemasListPage.tsx:123-162` |
| Owner | `/owner/schemas/:schemaId/design` / `OwnerSchemaDesignerPage` | Designer canvas + builder + inspector。 | 1024px 已有单列断点; 更小屏风险在拖拽、字段属性面板、发布/版本历史操作顺序。 | `apps/web/src/app/router.tsx:142-149`, `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:205-285`, `apps/web/src/app/styles.css:2923-2948`, `apps/web/src/app/styles.css:3059-3105` |
| PA | `/platform/llm` / `OwnerLlmSettingsPage` | LLM provider 设置双栏 grid + scope switches。 | 420px 侧栏轨道、表单双列、固定宽 SideSheet/输入。 | `apps/web/src/app/router.tsx:152-159`, `apps/web/src/pages/owner/OwnerLlmSettingsPage.tsx:727-770`, `apps/web/src/app/styles.css:5906-5912`, `apps/web/src/app/styles.css:6123-6130` |
| PA | `/platform/cost-metrics` / `PlatformCostDashboardPage` | 成本 summary + trend + 多个 4 列 CostTable。 | trend row 的多列网格、CostTable 横滚。 | `apps/web/src/app/router.tsx:162-169`, `apps/web/src/pages/platform/PlatformCostDashboardPage.tsx:72-94`, `apps/web/src/pages/platform/PlatformCostDashboardPage.tsx:134-176`, `apps/web/src/app/styles.css:1571-1649` |
| PA | `/platform/labor-metrics` / `PlatformLaborMetricsPage` | submission/review 人力 Table; review 表最多 6 列。 | review 指标列横向可读性。 | `apps/web/src/app/router.tsx:172-179`, `apps/web/src/pages/platform/PlatformLaborMetricsPage.tsx:37-64`, `apps/web/src/app/styles.css:1666-1768` |
| PA | `/platform/efficiency-metrics` / `PlatformEfficiencyMetricsPage` | 指标卡片与 unit grid,无 Table。 | 长 caption 与数字卡片换行。 | `apps/web/src/app/router.tsx:182-189`, `apps/web/src/pages/platform/PlatformEfficiencyMetricsPage.tsx:55-105`, `apps/web/src/app/styles.css:1672-1676`, `apps/web/src/app/styles.css:1765-1768` |
| PA | `/platform/audit-logs` / `OwnerAuditLogsPage` | filter bar + 5 列 Table + payload Modal width 720。 | DatePicker range、payload preview、Modal 固定宽。 | `apps/web/src/app/router.tsx:192-199`, `apps/web/src/pages/owner/OwnerAuditLogsPage.tsx:90-128`, `apps/web/src/pages/owner/OwnerAuditLogsPage.tsx:152-187`, `apps/web/src/app/styles.css:2337-2358` |
| PA | `/platform/users` / `UserManagementPage` | 6 列 account Table + pagination + Popconfirm。 | 角色 badge 堆叠、停用操作、Popconfirm 位置。 | `apps/web/src/app/router.tsx:202-209`, `apps/web/src/pages/admin/UserManagementPage.tsx:39-126`, `apps/web/src/pages/admin/UserManagementPage.tsx:140-190` |
| PA | `/platform/user-roles` / `UserRoleGrantPage` | 6 列 role Table,行内 Select 与授权/撤销。 | 行内 Select 与双操作按钮在卡片/横滚下可达。 | `apps/web/src/app/router.tsx:212-219`, `apps/web/src/pages/admin/UserRoleGrantPage.tsx:56-139`, `apps/web/src/pages/admin/UserRoleGrantPage.tsx:153-205` |
| PA | `/platform/change-password` / `PlatformPasswordChangePage` | 静态提示页 + 返回链接,当前没有改密表单。 | 文案换行与返回入口可达即可; 后续若加表单需重新取证。 | `apps/web/src/app/router.tsx:222-229`, `apps/web/src/pages/platform/PlatformPasswordChangePage.tsx:5-29` |

## 待证与挂起风险

1. 本文未运行浏览器截图,不能证明当前小屏视觉已经可用;只能证明已有断点、组件用法和潜在改造面。证据: 本任务约束为纯调研,且 M7-P2 旧截图集本身记录 live seeded-data gap: `docs/internal/m7p2-verification.md:302-317`。
2. Semi Grid 具备官方响应式能力,但本项目无 Row/Col/Grid import;是否引入 Grid 属于未来实现决策,本文不做方案结论。证据: Semi Grid 官方文档;本地 `rg "Row|Col|Grid"` 无命中。
3. Table 到 List 卡片的转换不能只改 CSS: Queue 行选择、批量审核、分页与操作入口都在 `ReviewerQueuePage` 组件状态中。证据: `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:42-44`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:106-131`, `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:235-253`。
