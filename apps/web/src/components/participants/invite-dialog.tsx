'use client';
import { Copy, Link2, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { useEnrollmentMutations, useInviteLinks } from '@/features/enrollment/use-enrollment';
import { COURSE_ROLES, type CourseRole } from '@/lib/api/schemas/common';
import { copyToClipboard } from '@/lib/utils/download';
import { formatDateTime } from '@/lib/utils/format';
import { addDays } from '@/lib/utils/time';

const DEFAULT_TTL_DAYS = 7;

/** FR-ENROL-03: invite link with role, TTL and use limit; the token URL is shown exactly once. */
export function InviteDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const [role, setRole] = useState<CourseRole>('student');
  const [ttlDays, setTtlDays] = useState(String(DEFAULT_TTL_DAYS));
  const [maxUses, setMaxUses] = useState('');
  const [createdUrl, setCreatedUrl] = useState<string | null>(null);
  const links = useInviteLinks(courseId, open);
  const { createInviteLink, revokeInviteLink } = useEnrollmentMutations(courseId);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setCreatedUrl(null);
      }}
    >
      <DialogTrigger asChild>
        <Button>
          <Link2 aria-hidden /> {t('invite')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('inviteTitle')}
        description={t('inviteHint')}
        closeLabel={tCommon('close')}
        className="max-w-xl"
      >
        {createdUrl ? (
          <div className="flex flex-col gap-3">
            <Alert tone="warning" title={t('shownOnce')} />
            <div className="flex gap-2">
              <Input
                readOnly
                value={createdUrl}
                aria-label={t('inviteUrl')}
                onFocus={(event) => event.target.select()}
              />
              <Button
                onClick={() =>
                  void copyToClipboard(createdUrl).then((ok) =>
                    toast({
                      tone: ok ? 'success' : 'error',
                      title: ok ? t('copied') : t('copyFailed'),
                    }),
                  )
                }
              >
                <Copy aria-hidden /> {t('copy')}
              </Button>
            </div>
          </div>
        ) : (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              createInviteLink.mutate(
                {
                  role,
                  expiresAt: ttlDays
                    ? addDays(new Date().toISOString(), Number(ttlDays))
                    : undefined,
                  maxUses: maxUses ? Number(maxUses) : undefined,
                },
                { onSuccess: ({ url }) => setCreatedUrl(url) },
              );
            }}
          >
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label={t('role')}>
                <NativeSelect
                  value={role}
                  onChange={(event) => setRole(event.target.value as CourseRole)}
                >
                  {COURSE_ROLES.map((option) => (
                    <option key={option} value={option}>
                      {tRoles(option)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              <Field label={t('ttlDays')}>
                <Input
                  type="number"
                  min={1}
                  value={ttlDays}
                  onChange={(event) => setTtlDays(event.target.value)}
                />
              </Field>
              <Field label={t('maxUses')} hint={t('unlimitedHint')}>
                <Input
                  type="number"
                  min={1}
                  value={maxUses}
                  onChange={(event) => setMaxUses(event.target.value)}
                />
              </Field>
            </div>
            <DialogFooter>
              <Button type="submit" loading={createInviteLink.isPending}>
                {t('createLink')}
              </Button>
            </DialogFooter>
          </form>
        )}
        {links.data && links.data.length > 0 ? (
          <section className="flex flex-col gap-2 border-t border-border pt-4">
            <h3 className="text-sm font-semibold">{t('activeLinks')}</h3>
            <ul className="flex flex-col gap-1.5">
              {links.data.map((link) => (
                <li key={link.id} className="flex items-center gap-2 text-sm">
                  <span className="flex-1">
                    {tRoles(link.role)}
                    {link.expiresAt
                      ? ` · ${t('until', { date: formatDateTime(link.expiresAt, locale) })}`
                      : ''}
                    {link.maxUses
                      ? ` · ${t('uses', { used: link.uses ?? 0, max: link.maxUses })}`
                      : ''}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t('revoke')}
                    onClick={() => revokeInviteLink.mutate(link.id)}
                  >
                    <Trash2 aria-hidden />
                  </Button>
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
