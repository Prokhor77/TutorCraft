'use client';
import { useTranslations } from 'next-intl';
import type { AccountCreator, AccountOrigin } from '@/lib/api/schemas/members';

/** «Пригласил репетитор · Анна Смирнова» — how the account appeared and who created it. */
export function AccountOriginCell({
  origin,
  createdBy,
}: {
  origin: AccountOrigin;
  createdBy: AccountCreator | null;
}) {
  const t = useTranslations('members');
  return (
    <span className="flex min-w-0 flex-col">
      <span className="truncate text-sm">{t(`origins.${origin}`)}</span>
      {createdBy ? (
        <span className="truncate text-xs text-text-muted" title={createdBy.email}>
          {t('createdBy', { name: `${createdBy.firstName} ${createdBy.lastName}`.trim() })}
        </span>
      ) : null}
    </span>
  );
}
