import { Button, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { useState } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { getAccessToken } from '../../../../shared/api/auth-storage';
import { ReadOnlyValue } from './FieldFrame';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

type LlmValue = {
  input?: string;
  output?: unknown;
  aiCallId?: number;
};

export function LabelHubLlmInteractionField({ field, sessionId }: { field?: SchemaField; sessionId?: number }) {
  const formilyField = useField<FormilyField>();
  const [loading, setLoading] = useState(false);
  const value = asLlmValue(formilyField.value);

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={JSON.stringify(value.output ?? value.input ?? '', null, 2)} />;
  }

  const runAssist = async () => {
    if (!sessionId || !field) return;
    const token = getAccessToken();
    if (!token) {
      Toast.error('登录已过期,请重新登录');
      return;
    }
    setLoading(true);
    try {
      const response = await fetch(`${API_BASE}/ai-review/field-assist`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId,
          fieldPath: field.stableId,
          input: { prompt: field.aiPrompt, text: value.input ?? '' },
        }),
      });
      if (!response.ok) throw new Error('field assist failed');
      const body = (await response.json()) as { aiCallId: number; output: unknown };
      formilyField.setValue({ ...value, output: body.output, aiCallId: body.aiCallId });
    } catch {
      Toast.error('LLM 生成失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="labelhub-llm-field">
      <TextArea
        autosize
        value={value.input ?? ''}
        placeholder={field?.placeholder ?? '输入给 LLM 的内容'}
        onChange={(input) => formilyField.setValue({ ...value, input })}
      />
      <Button loading={loading} disabled={!sessionId || !field} onClick={() => void runAssist()}>
        生成建议
      </Button>
      {value.output ? <Typography.Paragraph>{JSON.stringify(value.output, null, 2)}</Typography.Paragraph> : null}
    </div>
  );
}

function asLlmValue(value: unknown): LlmValue {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as LlmValue) : {};
}
