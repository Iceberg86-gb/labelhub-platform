# 设计系统

LabelHub 的视觉语言：克制的浅色界面，冷灰绿侧栏渐变，蓝色只用作点缀，主操作用黑色，语义色保持低饱和，圆角柔和，排版安静。

**产品原则：AI 给出的信息在视觉上要比人类给出的结论更弱。** AI 预审相关的元素有意做成低对比、中性色；人工审核和最终裁决的状态则承担更强的视觉层级。这是"AI 辅助 + 人类问责"在界面上的体现。

## 运行时 token（唯一事实来源）

设计 token 以 CSS 变量的形式，定义在 [`apps/web/src/shared/ui/tokens.css`](../../apps/web/src/shared/ui/tokens.css)，并由 `app/main.tsx` 全局加载一次。

在 TypeScript 里需要引用 token 时，使用 [`apps/web/src/shared/ui/tokens.ts`](../../apps/web/src/shared/ui/tokens.ts) 导出的 `var(--…)` 引用，不要复制十六进制色值。

组件使用规范见 [`apps/web/src/shared/ui/README.md`](../../apps/web/src/shared/ui/README.md)；合规检查可以运行 `.claude/skills/design-system-review`。

## 设计资产

| 目录 | 内容 |
|---|---|
| `icons/` | 24px 线性 SVG 图标，颜色使用 `currentColor` |
| `illustrations/` | 空状态插画，用 `--lh-illustration-*` 变量控制主题 |
| `status/` | 审核流程图和状态标签 |
| `hero/` | 欢迎页和登录页的矢量主视觉 |
| `explorations/` | 已定稿的图像生成探索稿，仅供参考，不在运行时使用 |

插画的主题变量示例：

```css
.lh-empty-state {
  --lh-illustration-base: var(--color-text-secondary);
  --lh-illustration-muted: var(--color-text-tertiary);
  --lh-illustration-soft: var(--color-surface-subtle);
  --lh-illustration-accent: var(--color-accent-blue);
}
```
