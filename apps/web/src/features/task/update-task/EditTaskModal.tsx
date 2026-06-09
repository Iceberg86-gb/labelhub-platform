import { Button, Form, Modal, Toast } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useEffect, useRef } from 'react';
import type { Task } from '../list-tasks/useTasksQuery';
import { UpdateTaskFailure, useUpdateTaskMutation } from './useUpdateTaskMutation';

type EditTaskFormValues = {
  title: string;
  description?: string;
  instructionRichText?: string;
  rewardRuleJson?: string;
  deadlineAt?: Date | string;
  tags?: string[];
};

type EditTaskModalProps = {
  task: Task;
  visible: boolean;
  onClose: () => void;
};

function normalizeDeadline(value?: Date | string) {
  if (!value) return undefined;
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

function isPastDate(value?: Date | string) {
  const deadline = value instanceof Date ? value : value ? new Date(value) : undefined;
  return deadline ? deadline.getTime() <= Date.now() : false;
}

function formatRewardRule(value: Task['rewardRule']) {
  return value ? JSON.stringify(value, null, 2) : '';
}

function parseRewardRule(value?: string) {
  const trimmed = value?.trim();
  if (!trimmed) return undefined;
  const parsed = JSON.parse(trimmed) as unknown;
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('奖励规则必须是 JSON object');
  }
  return parsed as Record<string, unknown>;
}

export function EditTaskModal({ task, visible, onClose }: EditTaskModalProps) {
  const updateTask = useUpdateTaskMutation();
  const formApiRef = useRef<FormApi<EditTaskFormValues>>();

  useEffect(() => {
    if (!visible) return;
    formApiRef.current?.setValues({
      title: task.title,
      description: task.description,
      instructionRichText: task.instructionRichText,
      rewardRuleJson: formatRewardRule(task.rewardRule),
      deadlineAt: task.deadlineAt,
      tags: task.tags,
    });
  }, [task, visible]);

  const handleSubmit = async (values: EditTaskFormValues) => {
    const deadlineAt = normalizeDeadline(values.deadlineAt);
    if (!deadlineAt) {
      formApiRef.current?.setError('deadlineAt', '请选择截止时间');
      return;
    }
    if (isPastDate(values.deadlineAt)) {
      formApiRef.current?.setError('deadlineAt', '截止时间必须晚于当前时间');
      return;
    }

    let rewardRule: Record<string, unknown> | undefined;
    try {
      rewardRule = parseRewardRule(values.rewardRuleJson);
    } catch (error) {
      formApiRef.current?.setError('rewardRuleJson', error instanceof Error ? error.message : '奖励规则 JSON 无效');
      return;
    }

    try {
      await updateTask.mutateAsync({
        taskId: task.id,
        body: {
          title: values.title,
          description: values.description,
          instructionRichText: values.instructionRichText,
          rewardRule,
          deadlineAt,
          tags: values.tags?.filter(Boolean),
        },
      });
      Toast.success('任务信息已更新。');
      onClose();
    } catch (error) {
      const failure = error as UpdateTaskFailure;
      Toast.error(failure.userMessage);
    }
  };

  const handleCancel = () => {
    if (!updateTask.isPending) {
      onClose();
    }
  };

  return (
    <Modal
      title="编辑任务信息"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      closeOnEsc={!updateTask.isPending}
      maskClosable={!updateTask.isPending}
      width={640}
    >
      <Form<EditTaskFormValues>
        layout="vertical"
        initValues={{
          title: task.title,
          description: task.description,
          instructionRichText: task.instructionRichText,
          rewardRuleJson: formatRewardRule(task.rewardRule),
          deadlineAt: task.deadlineAt,
          tags: task.tags,
        }}
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
        <Form.TextArea field="description" label="任务描述" autosize={{ minRows: 2, maxRows: 5 }} />
        <Form.TextArea field="instructionRichText" label="富文本说明" autosize={{ minRows: 2, maxRows: 5 }} />
        <Form.TextArea field="rewardRuleJson" label="奖励规则 JSON" autosize={{ minRows: 2, maxRows: 5 }} placeholder='例如 {"type":"fixed","amount":10}' />
        <Form.DatePicker field="deadlineAt" label="截止时间" type="dateTime" rules={[{ required: true, message: '请选择截止时间' }]} />
        <Form.TagInput field="tags" label="标签" placeholder="输入标签后回车" />

        <div className="modal-actions">
          <Button onClick={handleCancel} disabled={updateTask.isPending}>
            取消
          </Button>
          <Button htmlType="submit" theme="solid" type="primary" loading={updateTask.isPending}>
            保存
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
