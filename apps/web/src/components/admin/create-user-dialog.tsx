'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { UserPlus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { useUserMutations } from '@/features/admin/use-admin';
import { applyServerFieldErrors } from '@/features/forms/server-errors';

/** FR-USER-01/03: create a user and optionally send the email invitation. */
export function CreateUserDialog() {
  const t = useTranslations('adminUsers');
  const tAuth = useTranslations('auth');
  const tCommon = useTranslations('common');
  const { create } = useUserMutations();
  const [open, setOpen] = useState(false);
  const schema = z.object({
    email: z.string().trim().email(tAuth('validation.email')),
    firstName: z.string().trim().min(1, tAuth('validation.required')),
    lastName: z.string().trim().min(1, tAuth('validation.required')),
    admin: z.boolean(),
    sendInvite: z.boolean(),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', firstName: '', lastName: '', admin: false, sendInvite: true },
  });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <UserPlus aria-hidden /> {t('create')}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('createTitle')} closeLabel={tCommon('close')}>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={form.handleSubmit((values) =>
            create.mutate(
              {
                email: values.email,
                firstName: values.firstName,
                lastName: values.lastName,
                tenantRoles: values.admin ? ['tenant_admin'] : [],
                sendInvite: values.sendInvite,
              },
              {
                onSuccess: () => {
                  form.reset();
                  setOpen(false);
                },
                onError: (error) =>
                  applyServerFieldErrors(error, form.setError, ['email', 'firstName', 'lastName']),
              },
            ),
          )}
        >
          <Field label={tAuth('email')} error={form.formState.errors.email?.message} required>
            <Input type="email" {...form.register('email')} />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field
              label={tAuth('firstName')}
              error={form.formState.errors.firstName?.message}
              required
            >
              <Input {...form.register('firstName')} />
            </Field>
            <Field
              label={tAuth('lastName')}
              error={form.formState.errors.lastName?.message}
              required
            >
              <Input {...form.register('lastName')} />
            </Field>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.watch('admin')}
              onCheckedChange={(checked) => form.setValue('admin', checked === true)}
            />
            {t('makeAdmin')}
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.watch('sendInvite')}
              onCheckedChange={(checked) => form.setValue('sendInvite', checked === true)}
            />
            {t('sendInvite')}
          </label>
          <DialogFooter>
            <Button type="submit" loading={create.isPending}>
              {tCommon('create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
