import { Button } from './button';

export function LoadMore({
  hasMore,
  loading,
  onClick,
  label,
}: {
  hasMore: boolean;
  loading: boolean;
  onClick: () => void;
  label: string;
}) {
  if (!hasMore) return null;
  return (
    <div className="flex justify-center pt-2">
      <Button variant="secondary" size="sm" loading={loading} onClick={onClick}>
        {label}
      </Button>
    </div>
  );
}
