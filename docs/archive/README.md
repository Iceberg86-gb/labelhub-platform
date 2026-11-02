# 归档

已经封闭的里程碑（M2–M7）留下的过程工件。**只读，不再更新。** 这里保留的是"当时如何决策、如何验证"的原始证据，现行规则以 [`docs/`](../README.md) 下的其他目录为准。

- `milestones/`：每个批次的 `*-pre-estimate.md`（预估）、`*-scope-budget.md`（范围预算）、`*-verification.md`（验证报告），以及专题调研 `*-research.md`。
- `screenshots/`：各里程碑的截图证据索引，入口是 [`screenshots/INDEX.md`](screenshots/INDEX.md)。**图片本身（118 张截图，解压后约 80MB）已移出 git 仓库**，打包为 Release [`archive-2026-11-02`](https://github.com/Iceberg86-gb/labelhub-platform/releases/tag/archive-2026-11-02) 的附件 `labelhub-archive-screenshots-m2-m7.zip`。解压到 `docs/archive/` 后，索引里的相对链接即可恢复。

批次之间的交付与验证主线，记录在 [`docs/process/humanpending.md`](../process/humanpending.md)。

2026-11 仓库瘦身前的完整 git 历史（包括答辩视频和全部截图）以 git bundle 的形式保存在同一个 Release 里（`labelhub-full-history-2026-11-02.bundle`），可以用 `git clone labelhub-full-history-2026-11-02.bundle` 完整恢复。
