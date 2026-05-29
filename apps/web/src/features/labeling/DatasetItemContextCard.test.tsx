import { renderToString } from 'react-dom/server';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { DatasetItemContextCard, selectDatasetItemPayload } from './DatasetItemContextCard';

vi.mock('@douyinfe/semi-ui', () => ({
  Card: ({ children }: { children?: ReactNode }) => <section>{children}</section>,
  Space: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

describe('DatasetItemContextCard', () => {
  it('renders known QA fields from a frozen dataset item payload', () => {
    const html = renderToString(
      <DatasetItemContextCard
        itemPayload={{
          prompt: '光合作用主要发生在植物细胞的哪个结构中？',
          model_answer: '光合作用主要发生在叶绿体中。',
          reference: '叶绿体（类囊体薄膜）。',
          tags: ['生物', '基础科学'],
          difficulty: '简单',
          category: '知识问答',
          media_url: 'https://example.test/source',
          media_type: 'text',
        }}
      />,
    );

    expect(html).toContain('待评审内容');
    expect(html).toContain('光合作用主要发生在植物细胞的哪个结构中？');
    expect(html).toContain('光合作用主要发生在叶绿体中。');
    expect(html).toContain('叶绿体（类囊体薄膜）。');
    expect(html).toContain('生物');
    expect(html).toContain('基础科学');
    expect(html).toContain('简单');
    expect(html).toContain('知识问答');
    expect(html).toContain('href="https://example.test/source"');
  });

  it('keeps content_markdown as escaped plain text instead of unsafe HTML', () => {
    const html = renderToString(
      <DatasetItemContextCard
        itemPayload={{
          content_markdown: '<img src=x onerror=alert(1)>',
        }}
      />,
    );

    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(html).not.toContain('<img src=x');
  });

  it('renders unknown scalar and nested fields through the raw fallback', () => {
    const html = renderToString(
      <DatasetItemContextCard
        itemPayload={{
          custom_score: 0.82,
          nested: { source: 'fixture' },
        }}
      />,
    );

    expect(html).toContain('原始数据');
    expect(html).toContain('custom_score');
    expect(html).toContain('0.82');
    expect(html).toContain('nested');
    expect(html).toContain('fixture');
  });

  it('does not crash on malformed payload values', () => {
    expect(renderToString(<DatasetItemContextCard itemPayload={null} />)).toContain('暂无可展示的样本内容');
    expect(renderToString(<DatasetItemContextCard itemPayload="raw string payload" />)).toContain('raw string payload');
    expect(renderToString(<DatasetItemContextCard itemPayload={['a', 'b']} />)).toContain('a');
  });

  it('selects frozen claim snapshot payload before live dataset item payload', () => {
    const selected = selectDatasetItemPayload({
      claimSnapshot: { datasetItemPayload: { prompt: 'frozen prompt' } },
      datasetItemPayload: { prompt: 'live prompt' },
    });

    expect(selected).toEqual({ payload: { prompt: 'frozen prompt' }, source: 'claimSnapshot' });
  });

  it('falls back to live dataset item payload when frozen snapshot payload is absent', () => {
    const selected = selectDatasetItemPayload({
      claimSnapshot: {},
      datasetItemPayload: { prompt: 'live prompt' },
    });

    expect(selected).toEqual({ payload: { prompt: 'live prompt' }, source: 'datasetItem' });
  });
});
