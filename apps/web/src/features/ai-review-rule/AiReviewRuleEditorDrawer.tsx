import { Button, Input, SideSheet, Space, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconPlus } from '@douyinfe/semi-icons';
import { useState } from 'react';
import {
  buildAiReviewRuleRequest,
  createDefaultAiReviewRuleFormState,
  type AiReviewRuleFormErrors,
  type AiReviewRuleFormState,
} from './aiReviewRuleFormModel';
import { AiReviewRuleHistoryPanel } from './AiReviewRuleHistoryPanel';
import { AiReviewRuleMutationFailure, useSaveAiReviewRuleMutation } from './useSaveAiReviewRuleMutation';

type AiReviewRuleEditorDrawerProps = {
  taskId: number;
  open: boolean;
  onClose: () => void;
};

export function AiReviewRuleEditorDrawer({ taskId, open, onClose }: AiReviewRuleEditorDrawerProps) {
  const saveRule = useSaveAiReviewRuleMutation();
  const [state, setState] = useState<AiReviewRuleFormState>(() => createDefaultAiReviewRuleFormState());
  const [errors, setErrors] = useState<AiReviewRuleFormErrors>({});
  const [serverError, setServerError] = useState('');

  const updateDimension = (index: number, value: string) => {
    setState((current) => ({
      ...current,
      dimensions: current.dimensions.map((dimension, dimensionIndex) => (dimensionIndex === index ? value : dimension)),
    }));
  };

  const removeDimension = (index: number) => {
    setState((current) => ({
      ...current,
      dimensions: current.dimensions.filter((_, dimensionIndex) => dimensionIndex !== index),
    }));
  };

  const addDimension = () => {
    setState((current) => ({ ...current, dimensions: [...current.dimensions, ''] }));
  };

  const submit = async () => {
    const result = buildAiReviewRuleRequest(taskId, state);
    setServerError('');
    setErrors(result.ok ? {} : result.errors);
    if (!result.ok) {
      return;
    }

    try {
      const rule = await saveRule.mutateAsync(result.request);
      Toast.success(`已创建 AI 审核规则版本 v${rule.versionNo}`);
      setState(createDefaultAiReviewRuleFormState());
      setErrors({});
      onClose();
    } catch (error) {
      const message = error instanceof AiReviewRuleMutationFailure
        ? error.userMessage
        : error instanceof Error
          ? error.message
          : 'AI 审核规则保存失败';
      setServerError(message);
      Toast.error(message);
    }
  };

  return (
    <SideSheet
      title="AI 预审辅助配置"
      visible={open}
      width={640}
      maskStyle={{ backgroundColor: 'color-mix(in srgb, var(--color-primary-black) 34%, transparent)' }}
      onCancel={onClose}
    >
      <div className="ai-review-rule-editor ai-review-rule-editor--drawer">
        <section className="ai-review-rule-save-form ai-review-rule-save-form--structured" data-testid="ai-review-rule-save-form">
          <div className="ai-review-rule-editor__intro">
            <Typography.Title heading={5}>创建新的规则草稿</Typography.Title>
            <Typography.Text type="tertiary">
              保存会追加一个新的规则版本,不会覆盖既有配置。
            </Typography.Text>
          </div>

          <div className="ai-review-rule-assistive-note">
            <Typography.Text strong>AI 只提供预审证据</Typography.Text>
            <Typography.Text type="tertiary">通过/打回仍由人工审核员在审核台完成。</Typography.Text>
          </div>

          <label className="ai-review-rule-field">
            <Typography.Text strong>Prompt 模板</Typography.Text>
            <TextArea
              autosize={{ minRows: 5, maxRows: 9 }}
              placeholder="写下 AI 审核 submission 时应遵循的业务规则"
              value={state.promptTemplate}
              onChange={(value) => setState((current) => ({ ...current, promptTemplate: value }))}
            />
            {errors.promptTemplate ? <Typography.Text className="ai-review-rule-error">{errors.promptTemplate}</Typography.Text> : null}
          </label>

          <div className="ai-review-rule-field">
            <div className="ai-review-rule-field__head">
              <Typography.Text strong>评分维度</Typography.Text>
              <Button icon={<IconPlus />} size="small" onClick={addDimension}>
                添加维度
              </Button>
            </div>
            <div className="ai-review-rule-dimensions">
              {state.dimensions.map((dimension, index) => (
                <div className="ai-review-rule-dimension-row" key={index}>
                  <Input
                    placeholder="例如:准确性"
                    value={dimension}
                    onChange={(value) => updateDimension(index, value)}
                  />
                  <Button
                    aria-label={`删除评分维度 ${index + 1}`}
                    disabled={state.dimensions.length <= 1}
                    icon={<IconDelete />}
                    onClick={() => removeDimension(index)}
                  />
                </div>
              ))}
            </div>
            {errors.dimensions ? <Typography.Text className="ai-review-rule-error">{errors.dimensions}</Typography.Text> : null}
          </div>

          <label className="ai-review-rule-field">
            <Typography.Text strong>通过阈值</Typography.Text>
            <Input
              inputMode="decimal"
              placeholder="0 到 1,例如 0.8"
              value={state.passThreshold}
              onChange={(value) => setState((current) => ({ ...current, passThreshold: value }))}
            />
            {errors.passThreshold ? <Typography.Text className="ai-review-rule-error">{errors.passThreshold}</Typography.Text> : null}
          </label>

          <label className="ai-review-rule-field">
            <Typography.Text strong>打回阈值</Typography.Text>
            <Input
              inputMode="decimal"
              placeholder="0 到 1,且小于通过阈值,例如 0.2"
              value={state.rejectThreshold}
              onChange={(value) => setState((current) => ({ ...current, rejectThreshold: value }))}
            />
            {errors.rejectThreshold ? <Typography.Text className="ai-review-rule-error">{errors.rejectThreshold}</Typography.Text> : null}
            <Typography.Text type="tertiary">
              分数高于通过阈值会建议通过,低于打回阈值会建议打回,中间区建议人工复核。
            </Typography.Text>
          </label>

          {serverError ? <Typography.Text className="ai-review-rule-error">{serverError}</Typography.Text> : null}

          <Space className="ai-review-rule-editor__actions">
            <Button onClick={onClose}>取消</Button>
            <Button loading={saveRule.isPending} theme="solid" type="primary" onClick={submit}>
              保存草稿
            </Button>
          </Space>
        </section>

        <AiReviewRuleHistoryPanel taskId={taskId} />
      </div>
    </SideSheet>
  );
}
