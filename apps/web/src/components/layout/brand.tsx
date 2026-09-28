import Link from 'next/link';
import { cn } from '@/lib/utils/cn';

export const PRODUCT_NAME = 'TutorCraft';

/** TutorCraft logo mark (Stitch export, design/stitch/logo/logo.svg). Same artwork as public/icons/icon.svg. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" fill="none" aria-hidden className={cn('size-9 shrink-0', className)}>
      <rect width="40" height="40" rx="10" fill="#4F46E5" />
      <path d="M12 15L20 10L28 15L20 20L12 15Z" fill="#EEF2FF" />
      <path
        d="M14 18.5V24C14 26.5 17 28.5 20 28.5C23 28.5 26 26.5 26 24V18.5L20 22.25L14 18.5Z"
        fill="#C7D2FE"
      />
      <circle cx="28" cy="22" r="2.5" fill="#10B981" />
      <path d="M28 24.5V28" stroke="#10B981" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

/**
 * Logo + name. `eyebrow` renders the Stitch mobile header form: a small uppercase label above the name
 * (e.g. «TUTORCRAFT» over the current section).
 */
export function Brand({
  href = '/',
  logoUrl,
  name = PRODUCT_NAME,
  eyebrow,
  className,
}: {
  href?: string;
  logoUrl?: string | null;
  name?: string;
  eyebrow?: string;
  className?: string;
}) {
  return (
    <Link
      href={href}
      className={cn(
        'flex min-w-0 items-center gap-2.5 rounded-full font-heading text-lg font-bold tracking-tight text-text',
        className,
      )}
    >
      {logoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- tenant logo from pre-signed storage URL
        <img src={logoUrl} alt="" className="size-9 rounded-sm object-contain" />
      ) : (
        <LogoMark />
      )}
      {eyebrow ? (
        <span className="flex min-w-0 flex-col leading-none">
          <span className="truncate font-sans text-label-sm uppercase text-text-muted">
            {eyebrow}
          </span>
          <span className="truncate">{name}</span>
        </span>
      ) : (
        <span className="truncate">{name}</span>
      )}
    </Link>
  );
}
