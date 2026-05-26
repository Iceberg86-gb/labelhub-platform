import { Button, Popover } from '@douyinfe/semi-ui';
import { IconPlus } from '@douyinfe/semi-icons';
import type { SchemaFieldType } from '../../entities/schema/schemaTypes';
import { FieldTypePicker } from './FieldTypePicker';

type AddFieldButtonProps = {
  onPick: (type: SchemaFieldType) => void;
  excludeTypes?: SchemaFieldType[];
  label?: string;
};

export function AddFieldButton({ onPick, excludeTypes, label = '添加字段' }: AddFieldButtonProps) {
  return (
    <Popover
      trigger="click"
      position="bottomLeft"
      content={<FieldTypePicker onPick={onPick} excludeTypes={excludeTypes} />}
    >
      <Button icon={<IconPlus />} theme="solid" type="primary">
        {label}
      </Button>
    </Popover>
  );
}
