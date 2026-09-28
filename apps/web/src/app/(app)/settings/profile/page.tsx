'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Input, NativeSelect } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { toast } from '@/components/ui/toast';
import { PASSWORD_MIN_LENGTH_HINT } from '@/components/auth/register-form';
import { useMe, useUpdateMe } from '@/features/auth/use-auth';
import { useFileUpload } from '@/features/files/use-files';
import { applyServerFieldErrors } from '@/features/forms/server-errors';
import { meApi } from '@/lib/api/endpoints/me';
import { LOCALES } from '@/lib/api/schemas/auth';
import { fullName } from '@/lib/utils/format';
import { useRouter } from 'next/navigation';

const COMMON_TIMEZONES = [
  'Europe/Moscow',
  'Europe/Kaliningrad',
  'Europe/Samara',
  'Asia/Yekaterinburg',
  'Asia/Omsk',
  'Asia/Novosibirsk',
  'Asia/Krasnoyarsk',
  'Asia/Irkutsk',
  'Asia/Vladivostok',
  'Europe/London',
  'Europe/Berlin',
  'America/New_York',
  'UTC',
] as const;

function timezoneOptions(current: string): string[] {
  const supported =
    typeof Intl.supportedValuesOf === 'function'
      ? Intl.supportedValuesOf('timeZone')
      : [...COMMON_TIMEZONES];
  return supported.includes(current) ? supported : [current, ...supported];
}

/** FR-PROF-01: name, avatar, timezone, language; password change. */
export default function ProfilePage() {
  const t = useTranslations('profile');
  const tAuth = useTranslations('auth');
  const tPrefs = useTranslations('preferences');
  const tCommon = useTranslations('common');
  const router = useRouter();
  const me = useMe();
  const update = useUpdateMe();
  const avatar = useFileUpload('avatar');
  const profileSchema = z.object({
    firstName: z.string().trim().min(1, tAuth('validation.required')),
    lastName: z.string().trim().min(1, tAuth('validation.required')),
    timezone: z.string(),
    locale: z.enum(LOCALES),
  });
  const profileForm = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    values: me
      ? { firstName: me.firstName, lastName: me.lastName, timezone: me.timezone, locale: me.locale }
      : undefined,
  });
  const passwordSchema = z.object({
    currentPassword: z.string(),
    newPassword: z
      .string()
      .min(
        PASSWORD_MIN_LENGTH_HINT,
        tAuth('validation.passwordMin', { min: PASSWORD_MIN_LENGTH_HINT }),
      ),
  });
  const passwordForm = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '' },
  });
  const changePassword = useMutation({ mutationFn: meApi.changePassword });
  if (!me) return null;

  return (
    <>
      <PageHeader title={t('title')} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('personal')}</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              noValidate
              className="flex flex-col gap-4"
              onSubmit={profileForm.handleSubmit((values) =>
                update.mutate(values, {
                  onSuccess: (updated) => {
                    toast({ tone: 'success', title: t('saved') });
                    if (updated.locale !== document.documentElement.lang) router.refresh();
                  },
                  onError: (error) =>
                    applyServerFieldErrors(error, profileForm.setError, [
                      'firstName',
                      'lastName',
                      'timezone',
                      'locale',
                    ]),
                }),
              )}
            >
              <div className="flex items-center gap-4">
                <Avatar name={fullName(me)} src={me.avatarUrl} size="lg" />
                <FileDropzone
                  className="flex-1 py-4"
                  title={t('avatar')}
                  browseLabel={t('uploadAvatar')}
                  accept="image/png,image/jpeg,image/webp"
                  multiple={false}
                  disabled={avatar.isUploading}
                  onFiles={async ([file]) => {
                    const meta = file ? await avatar.upload(file) : null;
                    if (meta) update.mutate({ avatarFileId: meta.id });
                  }}
                />
              </div>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field
                  label={tAuth('firstName')}
                  error={profileForm.formState.errors.firstName?.message}
                  required
                >
                  <Input autoComplete="given-name" {...profileForm.register('firstName')} />
                </Field>
                <Field
                  label={tAuth('lastName')}
                  error={profileForm.formState.errors.lastName?.message}
                  required
                >
                  <Input autoComplete="family-name" {...profileForm.register('lastName')} />
                </Field>
              </div>
              <Field label={tAuth('email')} hint={t('emailHint')}>
                <Input value={me.email} readOnly disabled />
              </Field>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label={t('timezone')}>
                  <NativeSelect {...profileForm.register('timezone')}>
                    {timezoneOptions(me.timezone).map((zone) => (
                      <option key={zone} value={zone}>
                        {zone}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                <Field label={tPrefs('language')}>
                  <NativeSelect {...profileForm.register('locale')}>
                    {LOCALES.map((locale) => (
                      <option key={locale} value={locale}>
                        {tPrefs(`languages.${locale}`)}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
              </div>
              <Button type="submit" loading={update.isPending} className="self-end">
                {tCommon('save')}
              </Button>
            </form>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('password')}</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              noValidate
              className="flex flex-col gap-4"
              onSubmit={passwordForm.handleSubmit((values) =>
                changePassword.mutate(
                  {
                    currentPassword: values.currentPassword || undefined,
                    newPassword: values.newPassword,
                  },
                  {
                    onSuccess: () => {
                      passwordForm.reset();
                      toast({
                        tone: 'success',
                        title: t('passwordChanged'),
                        description: t('otherSessionsRevoked'),
                      });
                    },
                    onError: (error) =>
                      applyServerFieldErrors(error, passwordForm.setError, [
                        'currentPassword',
                        'newPassword',
                      ]),
                  },
                ),
              )}
            >
              <Field
                label={t('currentPassword')}
                hint={t('currentPasswordHint')}
                error={passwordForm.formState.errors.currentPassword?.message}
              >
                <Input
                  type="password"
                  autoComplete="current-password"
                  {...passwordForm.register('currentPassword')}
                />
              </Field>
              <Field
                label={tAuth('newPassword')}
                error={passwordForm.formState.errors.newPassword?.message}
                required
              >
                <Input
                  type="password"
                  autoComplete="new-password"
                  {...passwordForm.register('newPassword')}
                />
              </Field>
              <Button type="submit" loading={changePassword.isPending} className="self-end">
                {t('changePassword')}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
