import { Button } from '@douyinfe/semi-ui';
import type { SchemaFieldType } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPES, SCHEMA_FIELD_TYPE_LABELS } from '../../entities/schema/schemaTypes';
import { StatusBadge } from '../../shared/ui';

type FieldTypePickerProps = {
  onPick: (type: SchemaFieldType) => void;
  excludeTypes?: SchemaFieldType[];
};

export function FieldTypePicker({ onPick, excludeTypes = [] }: FieldTypePickerProps) {
  const types = SCHEMA_FIELD_TYPES.filter((type) => !excludeTypes.includes(type));

  return (
    <div className="field-type-picker">
      {types.map((type) => (
        <Button key={type} className="field-type-button" onClick={() => onPick(type)}>
          <StatusBadge tone="accent" size="small">
            {SCHEMA_FIELD_TYPE_LABELS[type]}
          </StatusBadge>
        </Button>
      ))}
    </div>
  );
}
