import { Button, Form, Modal, Toast } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useRef } from 'react';
import { useCreateTask, type CreateTaskFailure } from './useCreateTask';

type CreateTaskFormValues = {
  title: string;
  description?: string;
  quotaTotal?: number;
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

function applyFieldErrors(formApi: FormApi<CreateTaskFormValues> | undefined, error: CreateTaskFailure) {
  error.fieldErrors?.forEach((fieldError) => {
    formApi?.setError(fieldError.field as keyof CreateTaskFormValues, fieldError.message);
  });
}

export function CreateTaskModal({ visible, onClose }: CreateTaskModalProps) {
  const createTask = useCreateTask();
  const formApiRef = useRef<FormApi<CreateTaskFormValues>>();

  const handleSubmit = async (values: CreateTaskFormValues) => {
    try {
      await createTask.mutateAsync({
        title: values.title,
        description: values.description,
        quotaTotal: Number(values.quotaTotal),
        deadlineAt: normalizeDeadline(values.deadlineAt),
        tags: values.tags?.filter(Boolean),
      });
      Toast.success('任务已创建。');
      formApiRef.current?.reset();
      onClose();
    } catch (error) {
      const taskError = error as CreateTaskFailure;
      applyFieldErrors(formApiRef.current, taskError);
      if (!taskError.fieldErrors?.length) {
        Toast.error(taskError.message);
      }
    }
  };

  const handleCancel = () => {
    if (!createTask.isPending) {
      formApiRef.current?.reset();
      onClose();
    }
  };

  return (
    <Modal
      title="创建任务"
      visible={visible}
      onCancel={handleCancel}
      footer={null}
      closeOnEsc={!createTask.isPending}
      maskClosable={!createTask.isPending}
      width={560}
    >
      <Form<CreateTaskFormValues>
        layout="vertical"
        getFormApi={(formApi) => {
          formApiRef.current = formApi;
        }}
        onSubmit={handleSubmit}
      >
        <Form.Input field="title" label="任务标题" rules={[{ required: true, message: '请输入任务标题' }]} />
        <Form.TextArea field="description" label="任务描述" autosize placeholder="给标注员的简短任务背景。" />
        <Form.InputNumber
          field="quotaTotal"
          label="配额"
          min={1}
          rules={[{ required: true, message: '请输入配额' }]}
          validator={(value) => (Number(value) > 0 ? '' : '配额必须大于 0')}
        />
        <Form.DatePicker
          field="deadlineAt"
          label="截止时间"
          type="dateTime"
          rules={[{ required: true, message: '请选择截止时间' }]}
          validator={(value) => (isPastDate(value) ? '截止时间必须晚于当前时间' : '')}
        />
        <Form.TagInput field="tags" label="标签" placeholder="输入标签后回车" />

        <div className="modal-actions">
          <Button onClick={handleCancel} disabled={createTask.isPending}>
            取消
          </Button>
          <Button htmlType="submit" theme="solid" type="primary" loading={createTask.isPending}>
            创建任务
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
