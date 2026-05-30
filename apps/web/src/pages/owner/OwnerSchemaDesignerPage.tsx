import { Banner, Button, Card, Empty, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import { IconArrowLeft, IconRefresh } from '@douyinfe/semi-icons';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  containsFieldStableId,
  createField,
  findFieldByStableId,
  removeFieldByStableId,
  updateFieldByStableId,
} from '../../entities/schema/fieldFactory';
import { previewJson } from '../../entities/schema/schemaPreview';
import { replaceSchemaFields, schemaFields } from '../../entities/schema/runtimeSchema';
import {
  errorsByStableId,
  validateSchemaForUI,
} from '../../entities/schema/schemaValidation';
import type { SchemaDocument, SchemaField, SchemaFieldType, SchemaVersion } from '../../entities/schema/schemaTypes';
import { DesignerFieldBuilder } from '../../features/schema-design/DesignerFieldBuilder';
import {
  designerTargetFromParentStableId,
  insertFieldIntoDesignerTarget,
} from '../../features/schema-design/designerDragModel';
import { PublishSchemaModal } from '../../features/schema-design/PublishSchemaModal';
import { VersionHistoryDrawer } from '../../features/schema-design/VersionHistoryDrawer';
import { FieldEditor } from '../../features/schema-design/field-editors/FieldEditor';
import { useSchemaCurrentVersionQuery } from '../../features/schema-design/useSchemaCurrentVersionQuery';
import { SchemaFormilyPreviewPanel } from '../../features/labeling/formily/preview/SchemaFormilyPreviewPanel';

function parseSchemaId(raw?: string) {
  const schemaId = Number(raw);
  return Number.isInteger(schemaId) && schemaId > 0 ? schemaId : null;
}

export function OwnerSchemaDesignerPage() {
  const navigate = useNavigate();
  const { schemaId: rawSchemaId } = useParams();
  const schemaId = parseSchemaId(rawSchemaId);
  const currentVersionQuery = useSchemaCurrentVersionQuery(schemaId ?? 0);
  const [draftDocument, setDraftDocument] = useState<SchemaDocument | null>(null);
  const [selectedStableId, setSelectedStableId] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [versionDrawerVisible, setVersionDrawerVisible] = useState(false);

  useEffect(() => {
    if (!currentVersionQuery.isLoading && currentVersionQuery.document) {
      setDraftDocument(currentVersionQuery.document);
      setSelectedStableId((previous) =>
        previous && containsFieldStableId(schemaFields(currentVersionQuery.document), previous) ? previous : null,
      );
      setIsDirty(false);
      if (import.meta.env.DEV) {
        console.log('SchemaDesigner current document', currentVersionQuery.document);
      }
    }
  }, [currentVersionQuery.document, currentVersionQuery.isLoading]);

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!isDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isDirty]);

  const validationErrors = useMemo(
    () => (draftDocument ? validateSchemaForUI(draftDocument) : []),
    [draftDocument],
  );
  const validationErrorsByField = useMemo(() => errorsByStableId(validationErrors), [validationErrors]);
  const selectedField = useMemo(
    () => (draftDocument ? findFieldByStableId(schemaFields(draftDocument), selectedStableId) : null),
    [draftDocument, selectedStableId],
  );
  const draftFields = useMemo(() => schemaFields(draftDocument), [draftDocument]);
  const jsonPreview = useMemo(
    () => (draftDocument ? previewJson(draftDocument) : ''),
    [draftDocument],
  );

  const handleFieldsChange = (nextFields: SchemaField[]) => {
    if (!draftDocument) return;
    setDraftDocument(replaceSchemaFields(draftDocument, nextFields));
    if (selectedStableId && !containsFieldStableId(nextFields, selectedStableId)) {
      setSelectedStableId(null);
    }
    setIsDirty(true);
  };

  const handleAddField = (type: SchemaFieldType, parentStableId?: string, index?: number): SchemaField | null => {
    if (!draftDocument) return null;
    const field = createField(type);
    const currentFields = schemaFields(draftDocument);
    const target = designerTargetFromParentStableId(parentStableId);
    const nextFields = insertFieldIntoDesignerTarget(currentFields, target, field, index);
    if (!nextFields) return null;
    setDraftDocument(replaceSchemaFields(draftDocument, nextFields));
    setSelectedStableId(field.stableId);
    setIsDirty(true);
    return field;
  };

  const handleDeleteField = (stableId: string) => {
    if (!draftDocument) return;
    const nextFields = removeFieldByStableId(schemaFields(draftDocument), stableId);
    setDraftDocument(replaceSchemaFields(draftDocument, nextFields));
    if (selectedStableId && !containsFieldStableId(nextFields, selectedStableId)) {
      setSelectedStableId(null);
    }
    setIsDirty(true);
  };

  const handleSelectedFieldChange = (updatedField: SchemaField) => {
    if (!draftDocument) return;
    const nextFields = updateFieldByStableId(schemaFields(draftDocument), updatedField.stableId, () => updatedField);
    setDraftDocument(replaceSchemaFields(draftDocument, nextFields));
    setIsDirty(true);
  };

  const handlePublishClick = () => {
    if (validationErrors.length > 0) {
      Toast.warning('请先修复字段错误再发布');
      return;
    }
    setPublishModalVisible(true);
  };

  const handlePublishSuccess = (newVersion: SchemaVersion) => {
    setPublishModalVisible(false);
    setDraftDocument(newVersion.schemaJson);
    setSelectedStableId((previous) =>
      previous && containsFieldStableId(schemaFields(newVersion.schemaJson), previous) ? previous : null,
    );
    setIsDirty(false);
    Toast.success(`已发布 v${newVersion.versionNumber}`);
  };

  if (!schemaId) {
    return (
      <section className="schema-designer-page">
        <Empty title="Schema ID 无效" description="请从 Schema 列表重新进入 Designer。" />
      </section>
    );
  }

  if (currentVersionQuery.isLoading) {
    return (
      <section className="schema-designer-page">
        <div className="task-state-panel">
          <Spin size="large" />
        </div>
      </section>
    );
  }

  if (currentVersionQuery.isError || !currentVersionQuery.schema || !draftDocument) {
    return (
      <section className="schema-designer-page">
        <div className="task-state-panel">
          <Empty
            title="Schema Designer 加载失败"
            description={currentVersionQuery.error instanceof Error ? currentVersionQuery.error.message : '请稍后重试。'}
          />
          <Button onClick={() => currentVersionQuery.refetch()}>重新加载</Button>
        </div>
      </section>
    );
  }

  const versionLabel = currentVersionQuery.version
    ? `当前版本: v${currentVersionQuery.version.versionNumber}`
    : '当前版本: 尚未发布';

  return (
    <section className="schema-designer-page" aria-label="Owner schema designer">
      <div className="schema-designer-canvas">
        <div className="detail-heading">
          <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate('/owner/schemas')}>
            返回 Schema 列表
          </Button>
          <Button icon={<IconRefresh />} onClick={() => currentVersionQuery.refetch()} loading={currentVersionQuery.isFetching}>
            刷新
          </Button>
        </div>

        <div className="schema-designer-header">
          <div>
            <Typography.Title heading={3} className="page-title">
              {currentVersionQuery.schema.name}
            </Typography.Title>
            <Typography.Text type="tertiary">{currentVersionQuery.schema.description || '暂无描述'}</Typography.Text>
          </div>
          <div className="schema-designer-actions">
            <div className="schema-version-pill">{versionLabel} | 草稿</div>
            <Button onClick={() => setVersionDrawerVisible(true)}>版本历史</Button>
            <Button theme="solid" type="primary" onClick={handlePublishClick}>
              发布版本
            </Button>
          </div>
        </div>

        <Banner
          type="warning"
          description="未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。"
          closeIcon={null}
        />

        <div className="schema-designer-grid">
          <DesignerFieldBuilder
            fields={draftFields}
            onChange={handleFieldsChange}
            onAddField={handleAddField}
            selectedStableId={selectedStableId}
            onSelect={setSelectedStableId}
            onDelete={handleDeleteField}
            errors={validationErrorsByField}
            validationErrorCount={validationErrors.length}
          />

          <Card className="schema-designer-panel">
            <div className="schema-designer-panel__header">
              <div>
                <Typography.Title heading={5}>字段属性</Typography.Title>
                <Typography.Text type="tertiary">选择左侧字段后编辑属性。</Typography.Text>
              </div>
            </div>
            {selectedField ? (
              <FieldEditor
                field={selectedField}
                onChange={handleSelectedFieldChange}
                errors={validationErrorsByField.get(selectedField.stableId) ?? []}
                errorsByField={validationErrorsByField}
                selectedStableId={selectedStableId}
                onSelect={setSelectedStableId}
                onDelete={handleDeleteField}
              />
            ) : (
              <div className="designer-placeholder">
                <Typography.Text type="tertiary">请从左侧选择字段编辑。</Typography.Text>
              </div>
            )}

            <details className="json-preview-collapse">
              <summary>JSON 预览</summary>
              <pre className="schema-json-preview">{jsonPreview}</pre>
            </details>
          </Card>

          <SchemaFormilyPreviewPanel schemaFields={draftFields} />
        </div>

        <PublishSchemaModal
          visible={publishModalVisible}
          schemaId={schemaId}
          draftDocument={draftDocument}
          currentVersionNumber={currentVersionQuery.version?.versionNumber ?? null}
          onClose={() => setPublishModalVisible(false)}
          onSuccess={handlePublishSuccess}
        />
        <VersionHistoryDrawer
          visible={versionDrawerVisible}
          schemaId={schemaId}
          currentVersionId={currentVersionQuery.schema.currentVersionId ?? null}
          onClose={() => setVersionDrawerVisible(false)}
        />
      </div>
    </section>
  );
}
