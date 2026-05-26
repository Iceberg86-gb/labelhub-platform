import { Button, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconCopyStroked } from '@douyinfe/semi-icons';

type TruncatedHashProps = {
  value?: string | null;
  prefixLength?: number;
  suffixLength?: number;
  ariaLabel?: string;
  className?: string;
};

function formatHash(value: string, prefixLength: number, suffixLength: number) {
  if (value.length <= prefixLength + suffixLength + 1) {
    return value;
  }

  return `${value.slice(0, prefixLength)}...${value.slice(-suffixLength)}`;
}

export function TruncatedHash({
  value,
  prefixLength = 10,
  suffixLength = 6,
  ariaLabel = '完整 hash',
  className,
}: TruncatedHashProps) {
  const normalized = value?.trim();

  if (!normalized) {
    return <Typography.Text type="tertiary">-</Typography.Text>;
  }

  const copyHash = async () => {
    try {
      await navigator.clipboard.writeText(normalized);
      Toast.success('已复制 hash');
    } catch {
      Toast.warning('复制失败,请手动选择 hash');
    }
  };

  return (
    <span className={['truncated-hash', className].filter(Boolean).join(' ')}>
      <Tooltip content={normalized}>
        <Typography.Text className="truncated-hash__value" aria-label={ariaLabel}>
          {formatHash(normalized, prefixLength, suffixLength)}
        </Typography.Text>
      </Tooltip>
      <Tooltip content="复制完整 hash">
        <Button
          aria-label="复制完整 hash"
          className="truncated-hash__copy"
          icon={<IconCopyStroked aria-hidden />}
          size="small"
          theme="borderless"
          type="tertiary"
          onClick={copyHash}
        />
      </Tooltip>
    </span>
  );
}
