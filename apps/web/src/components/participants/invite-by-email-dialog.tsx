'use client';
import { MailPlus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState, type FormEvent } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { useCourseInvitation } from '@/features/members/use-members';
import { COURSE_ROLES, type CourseRole } from '@/lib/api/schemas/common';
import { ActivationLink } from './activation-link';

const EMPTY_FORM = { email: '', firstName: '', lastName: '' };

/**
 * The teacher invites a person by email: the account is created in the school if needed, the person is enrolled,
 * and a one-time activation link is shown (the email is sent too when the server has SMTP).
 */
export function InviteByEmailDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tCommon = useTranslations('common');
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [role, setRole] = useState<CourseRole>('student');
  const [activationUrl, setActivationUrl] = useState<string | null>(null);
  const invite = useCourseInvitation(courseId);

  const reset = () => {
    setForm(EMPTY_FORM);
    setActivationUrl(null);
    invite.reset();
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    invite.mutate(
      { ...form, email: form.email.trim(), role },
      {
        onSuccess: (result) => {
          if (result.activationUrl) {
            setActivationUrl(result.activationUrl);
            return;
          }
          toast({ tone: 'success', title: t('invitedExisting') });
          reset();
          setOpen(false);
        },
      },
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) reset();
      }}
    >
      <DialogTrigger asChild>
        <Button variant="secondary">
          <MailPlus aria-hidden /> {t('inviteByEmail')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('inviteByEmailTitle')}
        description={t('inviteByEmailHint')}
        closeLabel={tCommon('close')}
        className="max-w-xl"
      >
        {activationUrl ? (
          <div className="flex flex-col gap-4">
            <Alert tone="success" title={t('invitedNew', { email: form.email.trim() })} />
            <ActivationLink url={activationUrl} />
            <DialogFooter>
              <Button variant="secondary" onClick={reset}>
                {t('inviteAnother')}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <form className="flex flex-col gap-4" onSubmit={submit}>
            <Field label={t('email')}>
              <Input
                type="email"
                required
                autoComplete="off"
                maxLength={254}
                value={form.email}
                onChange={(event) => setForm({ ...form, email: event.target.value })}
              />
            </Field>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label={t('firstName')}>
                <Input
                  required
                  maxLength={100}
                  value={form.firstName}
                  onChange={(event) => setForm({ ...form, firstName: event.target.value })}
                />
              </Field>
              <Field label={t('lastName')}>
                <Input
                  required
                  maxLength={100}
                  value={form.lastName}
                  onChange={(event) => setForm({ ...form, lastName: event.target.value })}
                />
              </Field>
            </div>
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
            <DialogFooter>
              <Button type="submit" loading={invite.isPending}>
                {t('sendInvitation')}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
