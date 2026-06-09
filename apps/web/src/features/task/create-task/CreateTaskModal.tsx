import { Button, Form, Modal, Select, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useMemo, useRef, useState } from 'react';
import { useApplySchemaTemplateToTaskMutation } from '../../schema-design/useApplySchemaTemplateToTaskMutation';
import { useSchemasQuery } from '../../schema-design/useSchemasQuery';
import { useCreateTask, type CreateTaskFailure } from './useCreateTask';

type CreateTaskFormValues = {
  title: string;
  description?: string;
  instructionRichText?: string;
  rewardRuleJson?: string;
  deadlineAt?: Date | string;
  tags?: string[];
};

type CreateTaskModalProps = {
  visible: boolean;
  onClose: () => void;
};

function normalizeDeadline(value?: Date | string) {
  if (!value) {
    return undefined;
  }
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

function isPastDate(value?: Date | string) {
  const deadline = value instanceof Date ? value : value ? new Date(value) : undefined;
  return deadline ? deadline.getTime() <= Date.now() : false;
}

function parseRewardRule(value?: string) {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = JSON.parse(trimmed) as unknown;
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('奖励规则必须是 JSON object');
  }
  return parsed as Record<string, unknown>;
}

function applyFieldErrors(formApi: FormApi<CreateTaskFormValues> | undefined, error: CreateTaskFailure) {
  error.fieldErrors?.forEach((fieldError) => {
    formApi?.setError(fieldError.field as keyof CreateTaskFormValues, fieldError.message);
  });
}

export function CreateTaskModal({ visible, onClose }: CreateTaskModalProps) {
  const createTask = useCreateTask();
  const applySchemaTemplate = useApplySchemaTemplateToTaskMutation();
  const schemaTemplates = useSchemasQuery({ page: 1, size: 100, enabled: visible });
  const formApiRef = useRef<FormApi<CreateTaskFormValues>>();
  const [selectedSchemaId, setSelectedSchemaId] = useState<number | undefined>();
  const isSubmitting = createTask.isPending || applySchemaTemplate.isPending;

  const schemaTemplateOptions = useMemo(
    () => (schemaTemplates.data?.items ?? [])
      .filter((schema) => schema.taskId == null && schema.currentVersionId != null)
      .map((schema) => ({
        label: schema.name,
        value: schema.id,
      })),
    [schemaTemplates.data?.items],
  );

  const handleSubmit = async (values: CreateTaskFormValues) => {
    const deadlineAt = normalizeDeadline(values.deadlineAt);
    if (!deadlineAt) {
      formApiRef.current?.setError('deadlineAt', '请选择截止时间');
      return;
    }
    let rewardRule: Record<string, unknown> | undefined;
    try {
      rewardRule = parseRewardRule(values.rewardRuleJson);
    } catch (error) {
      formApiRef.current?.setError('rewardRuleJson', error instanceof Error ? error.message : '奖励规则 JSON 无效');
      return;
    }

    let createdTaskId: number | undefined;
    try {
      const createdTask = await createTask.mutateAsync({
        title: values.title,
        description: values.description,
        instructionRichText: values.instructionRichText,
        rewardRule,
        deadlineAt,
        tags: values.tags?.filter(Boolean),
      });
      createdTaskId = createdTask.id;
      if (selectedSchemaId) {
        await applySchemaTemplate.mutateAsync({
          taskId: createdTask.id,
          schemaId: selectedSchemaId,
        });
      }
      Toast.success(selectedSchemaId ? '任务已创建并绑定模板。' : '任务已创建。');
      formApiRef.current?.reset();
      setSelectedSchemaId(undefined);
      onClose();
    } catch (error) {
      if (createdTaskId) {
        const message = typeof error === 'object' && error && 'message' in error ? String(error.message) : '模板绑定失败。';
        Toast.error(`任务已创建，但模板绑定失败：${message}`);
        formApiRef.current?.reset();
        setSelectedSchemaId(undefined);
        onClose();
        return;
      }
      const taskError = error as CreateTaskFailure;
      applyFieldErrors(formApiRef.current, taskError);
      if (!taskError.fieldErrors?.length) {
        Toast.error(taskError.message);
      }
    }
  };

  const handleCancel = () => {
    if (!isSubmitting) {
      formApiRef.current?.reset();
      setSelectedSchemaId(undefined);
      onClose();
    }
  };

  return (
    <Modal
      title="创建任务"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      closeOnEsc={!isSubmitting}
      maskClosable={!isSubmitting}
      width={560}
    >
      <Form<CreateTaskFormValues>
        layout="vertical"
        getFormApi={(formApi) => {
          formApiRef.current = formApi;
        }}
        onSubmit={handleSubmit}
      >
        <Form.Input
          className="task-form-title-input"
          field="title"
          label="任务标题"
          rules={[{ required: true, message: '请输入任务标题' }]}
        />
        <Form.TextArea field="description" label="任务描述" autosize={{ minRows: 2, maxRows: 5 }} placeholder="给标注员的简短任务背景。" />
        <Form.TextArea field="instructionRichText" label="富文本说明" autosize={{ minRows: 2, maxRows: 5 }} placeholder="支持 Markdown/富文本序列化内容,会在作答页展示。" />
        <Form.TextArea field="rewardRuleJson" label="奖励规则 JSON" autosize={{ minRows: 2, maxRows: 5 }} placeholder='例如 {"type":"fixed","amount":10}' />
        <Form.DatePicker
          field="deadlineAt"
          label="截止时间"
          type="dateTime"
          rules={[{ required: true, message: '请选择截止时间' }]}
          validator={(value) => (isPastDate(value) ? '截止时间必须晚于当前时间' : '')}
        />
        <Form.TagInput field="tags" label="标签" placeholder="输入标签后回车" />
        <div className="create-task-schema-template">
          <Typography.Text strong>Schema 模板</Typography.Text>
          <Select
            value={selectedSchemaId}
            onChange={(value) => setSelectedSchemaId(typeof value === 'number' ? value : undefined)}
            optionList={schemaTemplateOptions}
            placeholder={schemaTemplates.isError ? '模板加载失败，请刷新重试' : schemaTemplateOptions.length ? '可选：使用已有模板' : '暂无可用模板'}
            loading={schemaTemplates.isFetching}
            disabled={schemaTemplates.isLoading || !schemaTemplateOptions.length || isSubmitting}
            showClear
            style={{ width: '100%' }}
          />
        </div>

        <div className="modal-actions create-task-modal-actions">
          <Button onClick={handleCancel} disabled={isSubmitting}>
            取消
          </Button>
          <Button htmlType="submit" theme="solid" type="primary" loading={isSubmitting}>
            创建任务
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
