import { Button, Form, Modal, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useRef, useState } from 'react';
import type { Task, TaskStatus } from '../list-tasks/useTasksQuery';
import { actionLabelFor, transitionLabels } from './transitionRules';
import { useTransitionTask, type TransitionTaskFailure } from './useTransitionTask';

type TransitionFormValues = {
  reason: string;
};

type TransitionTaskModalProps = {
  task: Task;
  targetStatus: TaskStatus | null;
  onClose: () => void;
};

function errorMessage(error: TransitionTaskFailure) {
  const guard = error.fieldErrors?.[0];
  if (guard?.field === 'quota_total' || guard?.field === 'quotaTotal') {
    return '无法发布：配额必须大于 0。';
  }
  if (guard?.field === 'deadline_at' || guard?.field === 'deadlineAt') {
    return '无法发布：截止时间必须晚于当前时间。';
  }

  if (error.status === 409) {
    return '状态迁移冲突，请刷新后重试。';
  }

  return error.message;
}

export function TransitionTaskModal({ task, targetStatus, onClose }: TransitionTaskModalProps) {
  const transitionTask = useTransitionTask();
  const formApiRef = useRef<FormApi<TransitionFormValues>>();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const visible = targetStatus !== null;
  const actionLabel = targetStatus ? actionLabelFor(task.status, targetStatus) : '状态迁移';

  const handleSubmit = async (values: TransitionFormValues) => {
    if (!targetStatus) {
      return;
    }

    try {
      setSubmitError(null);
      await transitionTask.mutateAsync({
        taskId: task.id,
        toStatus: targetStatus,
        reason: values.reason.trim(),
      });
      Toast.success(`${actionLabel}成功。`);
      formApiRef.current?.reset();
      onClose();
    } catch (error) {
      const message = errorMessage(error as TransitionTaskFailure);
      setSubmitError(message);
      Toast.error(message);
    }
  };

  const handleClose = () => {
    if (!transitionTask.isPending) {
      setSubmitError(null);
      formApiRef.current?.reset();
      onClose();
    }
  };

  return (
    <Modal
      title={actionLabel}
      visible={visible}
      width={520}
      footer={null}
      closeOnEsc={!transitionTask.isPending}
      maskClosable={!transitionTask.isPending}
      onCancel={handleClose}
    >
      <div className="transition-summary">
        <Typography.Text type="secondary">目标状态</Typography.Text>
        <Typography.Text strong>
          {transitionLabels[task.status]} → {targetStatus ? transitionLabels[targetStatus] : '-'}
        </Typography.Text>
      </div>

      <Form<TransitionFormValues>
        layout="vertical"
        getFormApi={(formApi) => {
          formApiRef.current = formApi;
        }}
        onSubmit={handleSubmit}
      >
        {submitError ? (
          <div className="transition-error" role="alert">
            {submitError}
          </div>
        ) : null}

        <Form.TextArea
          field="reason"
          label="操作说明"
          autosize={{ minRows: 4, maxRows: 6 }}
          placeholder="说明本次状态迁移的原因，会写入 task_transitions 审计记录。"
          rules={[{ required: true, message: '请输入操作说明' }]}
          validator={(value) => {
            const reason = typeof value === 'string' ? value.trim() : '';
            if (!reason) {
              return '请输入操作说明';
            }
            if (reason.length > 500) {
              return '说明不超过 500 字';
            }
            return '';
          }}
        />

        <div className="modal-actions">
          <Button onClick={handleClose} disabled={transitionTask.isPending}>
            取消
          </Button>
          <Button htmlType="submit" theme="solid" type="primary" loading={transitionTask.isPending}>
            {actionLabel}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
