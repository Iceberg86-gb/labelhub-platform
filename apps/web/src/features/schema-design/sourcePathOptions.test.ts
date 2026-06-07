import { describe, expect, it } from 'vitest';
import type { DatasetItem } from '../../entities/dataset/datasetTypes';
import { buildSourcePathOptionsFromDatasetItems } from './sourcePathOptions';

describe('buildSourcePathOptionsFromDatasetItems', () => {
  it('expands dataset payload fields into nested sourcePath suggestions with type labels', () => {
    const options = buildSourcePathOptionsFromDatasetItems([
      {
        itemPayload: {
          question: { text: '题面' },
          items: [{ title: '回答 A' }],
          taskType: 'preference_compare',
        },
      } as unknown as DatasetItem,
    ]);

    expect(options).toEqual(expect.arrayContaining([
      { value: 'question.text', typeLabel: 'string' },
      { value: 'items.0.title', typeLabel: 'string' },
      { value: 'taskType', typeLabel: 'string' },
    ]));
  });
});
