import { Modal, Typography } from '@douyinfe/semi-ui';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { summarizeAnswerPayload } from '../../entities/submission/answerSummary';

interface SubmitConfirmModalProps {
  visible: boolean;
  fields: SchemaField[];
  payload: AnswerPayload;
  loading?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function SubmitConfirmModal({
  visible,
  fields,
  payload,
  loading = false,
  onClose,
  onConfirm,
}: SubmitConfirmModalProps) {
  const summary = summarizeAnswerPayload(fields, payload);

  return (
    <Modal
      title="确认提交"
      visible={visible}
      onCancel={onClose}
      onOk={onConfirm}
      okText="确认提交"
      cancelText="取消"
      confirmLoading={loading}
    >
      <div className="submit-modal-summary">
        <Typography.Text type="warning">提交后将无法修改答案,session 状态会变为已提交。</Typography.Text>
        <div className="answer-summary-grid">
          <span>总字段数</span>
          <strong>{summary.totalCount}</strong>
          <span>已回答字段</span>
          <strong>{summary.answeredCount}</strong>
          <span>未回答字段</span>
          <strong>{summary.unansweredCount}</strong>
        </div>
      </div>
    </Modal>
  );
}
