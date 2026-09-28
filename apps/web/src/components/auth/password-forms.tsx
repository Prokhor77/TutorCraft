'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { AtSign, Lock } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { IconInput, Input, PasswordInput } from '@/components/ui/input';
import { describeProblem } from '@/features/app/use-problem-toast';
import { ROUTES } from '@/features/auth/routes';
import { useAcceptInvitation } from '@/features/auth/use-auth';
import { applyServerFieldErrors } from '@/features/forms/server-errors';
import { authApi } from '@/lib/api/endpoints/auth';
import { hasProblemCode, PROBLEM_CODES } from '@/lib/api/problem';
import { AuthHeading } from './auth-heading';
import { PASSWORD_MIN_LENGTH_HINT } from './register-form';

const TOKEN_PARAM = 'token';

export function ForgotPasswordForm() {
  const t = useTranslations('auth');
  const [sent, setSent] = useState(false);
  const mutation = useMutation({ mutationFn: authApi.forgotPassword });
  const schema = z.object({ email: z.string().trim().email(t('validation.email')) });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: '' },
  });

  if (sent) {
    return (
      <div>
        <AuthHeading title={t('checkEmailTitle')} description={t('checkEmailText')} />
        <Button asChild variant="secondary" className="w-full">
          <Link href={ROUTES.login}>{t('backToLogin')}</Link>
        </Button>
      </div>
    );
  }
  return (
    <div>
      <AuthHeading title={t('forgotTitle')} description={t('forgotSubtitle')} />
      <form
        noValidate
        onSubmit={form.handleSubmit((values) =>
          mutation.mutate(values, { onSuccess: () => setSent(true) }),
        )}
        className="flex flex-col gap-4"
      >
        <Field
          labelVariant="caps"
          label={t('email')}
          error={form.formState.errors.email?.message}
          required
        >
          <IconInput
            icon={AtSign}
            type="email"
            placeholder={t('emailPlaceholder')}
            autoComplete="email"
            {...form.register('email')}
          />
        </Field>
        <Button type="submit" size="lg" loading={mutation.isPending}>
          {t('sendResetLink')}
        </Button>
        <Link href={ROUTES.login} className="text-center text-sm text-primary hover:underline">
          {t('backToLogin')}
        </Link>
      </form>
    </div>
  );
}

function usePasswordSchema() {
  const t = useTranslations('auth');
  return z
    .string()
    .min(PASSWORD_MIN_LENGTH_HINT, t('validation.passwordMin', { min: PASSWORD_MIN_LENGTH_HINT }));
}

export function ResetPasswordForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('errors');
  const router = useRouter();
  const token = useSearchParams().get(TOKEN_PARAM) ?? '';
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: authApi.resetPassword,
    meta: { skipErrorToast: true },
  });
  const schema = z.object({ newPassword: usePasswordSchema() });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '' },
  });

  if (!token) return <Alert tone="danger" title={t('tokenMissing')} />;
  return (
    <div>
      <AuthHeading title={t('resetTitle')} />
      {error ? <Alert tone="danger" className="mb-4" title={error} /> : null}
      <form
        noValidate
        onSubmit={form.handleSubmit((values) =>
          mutation.mutate(
            { token, newPassword: values.newPassword },
            {
              onSuccess: () => router.replace(`${ROUTES.login}?reset=1`),
              onError: (failure) => {
                if (hasProblemCode(failure, PROBLEM_CODES.tokenInvalid))
                  return setError(t('tokenInvalid'));
                if (!applyServerFieldErrors(failure, form.setError, ['newPassword']))
                  setError(describeProblem(failure, tErrors).title);
              },
            },
          ),
        )}
        className="flex flex-col gap-4"
      >
        <Field
          labelVariant="caps"
          label={t('newPassword')}
          error={form.formState.errors.newPassword?.message}
          required
        >
          <PasswordInput
            icon={Lock}
            showLabel={t('showPassword')}
            hideLabel={t('hidePassword')}
            autoComplete="new-password"
            {...form.register('newPassword')}
          />
        </Field>
        <Button type="submit" size="lg" loading={mutation.isPending}>
          {t('savePassword')}
        </Button>
      </form>
    </div>
  );
}

export function AcceptInvitationForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('errors');
  const router = useRouter();
  const token = useSearchParams().get(TOKEN_PARAM) ?? '';
  const accept = useAcceptInvitation();
  const [error, setError] = useState<string | null>(null);
  const schema = z.object({
    firstName: z.string().trim().min(1, t('validation.required')),
    lastName: z.string().trim().min(1, t('validation.required')),
    password: usePasswordSchema(),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', password: '' },
  });

  if (!token) return <Alert tone="danger" title={t('tokenMissing')} />;
  return (
    <div>
      <AuthHeading title={t('acceptInviteTitle')} description={t('acceptInviteSubtitle')} />
      {error ? <Alert tone="danger" className="mb-4" title={error} /> : null}
      <form
        noValidate
        onSubmit={form.handleSubmit((values) =>
          accept.mutate(
            { token, ...values },
            {
              onSuccess: () => router.replace(ROUTES.home),
              onError: (failure) => {
                if (hasProblemCode(failure, PROBLEM_CODES.tokenInvalid))
                  return setError(t('tokenInvalid'));
                if (
                  !applyServerFieldErrors(failure, form.setError, [
                    'firstName',
                    'lastName',
                    'password',
                  ])
                )
                  setError(describeProblem(failure, tErrors).title);
              },
            },
          ),
        )}
        className="flex flex-col gap-4"
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field
            labelVariant="caps"
            label={t('firstName')}
            error={form.formState.errors.firstName?.message}
            required
          >
            <Input autoComplete="given-name" {...form.register('firstName')} />
          </Field>
          <Field
            labelVariant="caps"
            label={t('lastName')}
            error={form.formState.errors.lastName?.message}
            required
          >
            <Input autoComplete="family-name" {...form.register('lastName')} />
          </Field>
        </div>
        <Field
          labelVariant="caps"
          label={t('password')}
          error={form.formState.errors.password?.message}
          required
        >
          <PasswordInput
            icon={Lock}
            showLabel={t('showPassword')}
            hideLabel={t('hidePassword')}
            autoComplete="new-password"
            {...form.register('password')}
          />
        </Field>
        <Button type="submit" size="lg" loading={accept.isPending}>
          {t('acceptInvite')}
        </Button>
      </form>
    </div>
  );
}
