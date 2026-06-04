import { renderToString } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { ReviewerAnswerSummary } from './ReviewerAnswerSummary';

describe('ReviewerAnswerSummary', () => {
  it('renders score badges, flattened ownership labels, and unanswered placeholders', () => {
    const fields: SchemaField[] = [
      { stableId: 'prompt_display', label: '题目', type: 'show_item', sourcePath: 'question.prompt' },
      { stableId: 'relevance_score', label: '相关性', type: 'single_select', options: [{ label: '5 分', value: '5' }] },
      { stableId: 'accuracy_score', label: '准确性', type: 'number' },
      { stableId: 'summary', label: '审核摘要', type: 'text' },
      {
        stableId: 'extra',
        label: '补充信息',
        type: 'nested_object',
        children: [
          { stableId: 'reason', label: '理由', type: 'rich_text' },
          { stableId: 'missing', label: '未答项', type: 'text' },
        ],
      },
      {
        stableId: 'tabs',
        label: '复核页组',
        type: 'tab_container',
        tabs: [
          {
            stableId: 'tab_a',
            label: '初审',
            children: [{ stableId: 'tab_note', label: '备注', type: 'text' }],
          },
        ],
      },
      { stableId: 'revision', label: '修订建议', type: 'json_editor' },
    ];

    const html = renderToString(
      <ReviewerAnswerSummary
        schemaFields={fields}
        answerPayload={{
          relevance_score: '5',
          accuracy_score: 5,
          summary: '人工审核通过',
          extra: { reason: '证据充分' },
          tab_note: '复核备注',
          revision: { patch: true },
        }}
        itemPayload={{ question: { prompt: '光合作用发生在哪里?' } }}
      />,
    );

    expect(html).toContain('reviewer-answer-summary');
    expect(html).toContain('题目');
    expect(html).toContain('光合作用发生在哪里?');
    expect(html).toContain('相关性');
    expect(html).toContain('5 分');
    expect(html).toContain('准确性');
    expect(html).toContain('审核摘要');
    expect(html).toContain('人工审核通过');
    expect(html).toContain('补充信息 · 理由');
    expect(html).toContain('证据充分');
    expect(html).toContain('补充信息 · 未答项');
    expect(html).toContain('未填写');
    expect(html).toContain('复核页组 · 初审 · 备注');
    expect(html).toContain('复核备注');
    expect(html).toContain('修订建议');
    expect(html).toContain('&quot;patch&quot;: true');
  });
});
