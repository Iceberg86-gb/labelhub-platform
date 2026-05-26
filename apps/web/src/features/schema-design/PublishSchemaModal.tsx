import { Button, Modal, Typography } from '@douyinfe/semi-ui';
import { useMemo, useState } from 'react';
import { summarizeSchema } from '../../entities/schema/schemaSummary';
import type { SchemaDocument, SchemaVersion } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPE_LABELS, SCHEMA_FIELD_TYPES } from '../../entities/schema/schemaTypes';
import { usePublishSchemaVersion, type PublishSchemaFailure } from './usePublishSchemaVersion';

type PublishSchemaModalProps = {
  visible: boolean;
  schemaId: number;
  draftDocument: SchemaDocument;
  currentVersionNumber: number | null;
  onClose: () => void;
  onSuccess: (newVersion: SchemaVersion) => void;
};

export function PublishSchemaModal({
  visible,
  schemaId,
  draftDocument,
  currentVersionNumber,
  onClose,
  onSuccess,
}: PublishSchemaModalProps) {
  const publishSchema = usePublishSchemaVersion();
  const [submitError, setSubmitError] = useState<PublishSchemaFailure | null>(null);
  const summary = useMemo(() => summarizeSchema(draftDocument), [draftDocument]);
  const nextVersionNumber = (currentVersionNumber ?? 0) + 1;

  const handleSubmit = async () => {
    try {
      setSubmitError(null);
      const newVersion = await publishSchema.mutateAsync({ schemaId, schemaJson: draftDocument });
      onSuccess(newVersion);
    } catch (error) {
      setSubmitError(error as PublishSchemaFailure);
    }
  };

  const handleClose = () => {
    if (!publishSchema.isPending) {
      setSubmitError(null);
      onClose();
    }
  };

  return (
    <Modal
      title="发布 Schema 新版本"
      visible={visible}
      width={620}
      footer={null}
      closeOnEsc={!publishSchema.isPending}
      maskClosable={!publishSchema.isPending}
      onCancel={handleClose}
    >
      <div className="publish-schema-modal">
        <div className="publish-schema-warning" role="note">
          发布后 Schema 内容不可再修改。所有基于此版本提交的标注数据将永久绑定该版本。
        </div>

        {submitError ? (
          <div className="publish-schema-error" role="alert">
            <Typography.Text strong>{submitError.message}</Typography.Text>
            {submitError.fieldErrors?.length ? (
              <ul>
                {submitError.fieldErrors.map((fieldError) => (
                  <li key={`${fieldError.field}-${fieldError.message}`}>
                    {fieldError.field}: {fieldError.message}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}

        <div className="publish-schema-summary">
          <div>
            <Typography.Text type="tertiary">即将发布</Typography.Text>
            <Typography.Title heading={5}>v{nextVersionNumber}</Typography.Title>
          </div>
          <div>
            <Typography.Text type="tertiary">字段总数</Typography.Text>
            <Typography.Title heading={5}>{summary.totalCount}</Typography.Title>
          </div>
          <div>
            <Typography.Text type="tertiary">顶层字段</Typography.Text>
            <Typography.Title heading={5}>{summary.topLevelCount}</Typography.Title>
          </div>
          <div>
            <Typography.Text type="tertiary">嵌套子字段</Typography.Text>
            <Typography.Title heading={5}>{summary.nestedCount}</Typography.Title>
          </div>
        </div>

        <div className="schema-type-distribution">
          {SCHEMA_FIELD_TYPES.filter((type) => summary.typeDistribution[type] > 0).map((type) => (
            <span key={type}>
              {SCHEMA_FIELD_TYPE_LABELS[type]}: {summary.typeDistribution[type]}
            </span>
          ))}
        </div>

        <div className="modal-actions">
          <Button onClick={handleClose} disabled={publishSchema.isPending}>
            取消
          </Button>
          <Button theme="solid" type="primary" loading={publishSchema.isPending} onClick={handleSubmit}>
            发布 v{nextVersionNumber}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
