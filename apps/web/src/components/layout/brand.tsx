import { GraduationCap } from 'lucide-react';
import Link from 'next/link';
import { cn } from '@/lib/utils/cn';

export const PRODUCT_NAME = 'TutorCraft';

export function Brand({
  href = '/',
  logoUrl,
  name = PRODUCT_NAME,
  className,
}: {
  href?: string;
  logoUrl?: string | null;
  name?: string;
  className?: string;
}) {
  return (
    <Link
      href={href}
      className={cn(
        'flex min-w-0 items-center gap-2 rounded-sm font-semibold text-text',
        className,
      )}
    >
      {logoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- tenant logo from pre-signed storage URL
        <img src={logoUrl} alt="" className="size-8 rounded-sm object-contain" />
      ) : (
        <span className="flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <GraduationCap className="size-5" aria-hidden />
        </span>
      )}
      <span className="truncate">{name}</span>
    </Link>
  );
}
