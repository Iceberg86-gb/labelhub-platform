import { describe, expect, it } from 'vitest';
import type { ExportFieldCatalog } from '../../entities/export/exportTypes';
import { buildFieldOptions, computeBindingPreview, type TrainingProfileBinding } from '../../features/export/exportBindingPreview';

const DPO_PROFILE: TrainingProfileBinding = {
  promptSource: 'item.prompt',
  completionSource: '',
  preferenceSource: 'answer.preferred',
  choiceASource: 'item.response_a',
  choiceBSource: 'item.response_b',
};

const SFT_PROFILE: TrainingProfileBinding = {
  promptSource: 'item.prompt',
  completionSource: 'answer.summary',
  preferenceSource: '',
  choiceASource: '',
  choiceBSource: '',
};

describe('computeBindingPreview', () => {
  it('counts DPO rows with prompt + chosen + rejected and exposes the real chosen/rejected values', () => {
    const rows = [
      { 'item.prompt': '解释过拟合', 'item.response_a': '正确解释', 'item.response_b': '错误解释', 'answer.preferred': 'A' },
      { 'item.prompt': '反转链表', 'item.response_a': '答案A2', 'item.response_b': '答案B2', 'answer.preferred': 'B' },
    ];

    const preview = computeBindingPreview('trl_dpo_jsonl', DPO_PROFILE, rows);

    expect(preview.total).toBe(2);
    expect(preview.validCount).toBe(2);
    const chosen = preview.slots.find((slot) => slot.label === '优选回答');
    const rejected = preview.slots.find((slot) => slot.label === '拒绝回答');
    expect(chosen?.source).toBe('item.response_a');
    expect(chosen?.value).toBe('正确解释');
    expect(rejected?.source).toBe('item.response_b');
    expect(rejected?.value).toBe('错误解释');
  });

  it('marks DPO rows invalid when the preference value does not match a choice key', () => {
    const rows = [
      { 'item.prompt': 'p', 'item.response_a': 'a', 'item.response_b': 'b', 'answer.preferred': 'C' },
    ];

    const preview = computeBindingPreview('trl_dpo_jsonl', DPO_PROFILE, rows);

    expect(preview.validCount).toBe(0);
  });

  it('surfaces the completion slot value for SFT so a verdict-style field is visible to the user', () => {
    const rows = [{ 'item.prompt': '解释过拟合', 'answer.summary': '答案A优于B' }];

    const preview = computeBindingPreview('openai_chat_sft_jsonl', SFT_PROFILE, rows);

    const completion = preview.slots.find((slot) => slot.label === '助手回答');
    expect(completion?.source).toBe('answer.summary');
    expect(completion?.value).toBe('答案A优于B');
    expect(preview.validCount).toBe(1);
  });

  it('treats flat_table and empty samples as non-blocking', () => {
    expect(computeBindingPreview('flat_table', SFT_PROFILE, [{ 'item.prompt': 'x' }]).validCount).toBe(1);
    const empty = computeBindingPreview('trl_dpo_jsonl', DPO_PROFILE, []);
    expect(empty.total).toBe(0);
    expect(empty.slots).toHaveLength(0);
  });
});

describe('buildFieldOptions', () => {
  it('builds options with coverage percentage and a sample snippet', () => {
    const catalog = {
      submissionCount: 2,
      fields: [
        { source: 'item.prompt', label: '用户问题', group: 'item', nonEmptyRatio: 1, sampleValues: ['解释过拟合'] },
        { source: 'answer.summary', label: '判断理由', group: 'answer', nonEmptyRatio: 0.5, sampleValues: ['答案A优于B'] },
      ],
      sampleRows: [],
    } as unknown as ExportFieldCatalog;

    const options = buildFieldOptions(catalog);

    expect(options[0]).toEqual({ value: 'item.prompt', label: '用户问题 · 覆盖 100%' });
    expect(options[1].label).toBe('判断理由 · 覆盖 50%');
  });
});
