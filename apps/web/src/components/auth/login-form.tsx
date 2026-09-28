'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { describeProblem } from '@/features/app/use-problem-toast';
import { NEXT_PARAM, ROUTES, safeNextPath } from '@/features/auth/routes';
import {
  useAuthProviders,
  useGoogleLogin,
  useLogin,
  useTelegramLogin,
} from '@/features/auth/use-auth';
import { isApiProblem, PROBLEM_CODES } from '@/lib/api/problem';
import {
  tenantChoiceSchema,
  type TelegramAuthPayload,
  type TenantChoice,
} from '@/lib/api/schemas/auth';
import { AuthHeading } from './auth-heading';
import { GoogleButton } from './google-button';
import { TelegramButton } from './telegram-button';

type PendingLogin =
  | { kind: 'password' }
  | { kind: 'google'; idToken: string }
  | { kind: 'telegram'; payload: TelegramAuthPayload };

function tenantsFromProblem(error: unknown): TenantChoice[] {
  if (!isApiProblem(error) || error.code !== PROBLEM_CODES.tenantRequired) return [];
  const parsed = z.array(tenantChoiceSchema).safeParse(error.args.tenants);
  return parsed.success ? parsed.data : [];
}

export function LoginForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('errors');
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNextPath(searchParams.get(NEXT_PARAM));
  const providers = useAuthProviders();
  const login = useLogin();
  const google = useGoogleLogin();
  const telegram = useTelegramLogin();
  const [tenants, setTenants] = useState<TenantChoice[]>([]);
  const [pending, setPending] = useState<PendingLogin | null>(null);
  const [error, setError] = useState<string | null>(null);

  const schema = z.object({
    email: z.string().trim().email(t('validation.email')),
    password: z.string().min(1, t('validation.required')),
  });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  const handleError = (failure: unknown, attempt: PendingLogin) => {
    const choices = tenantsFromProblem(failure);
    if (choices.length > 0) {
      setTenants(choices);
      setPending(attempt);
      return;
    }
    if (isApiProblem(failure) && failure.code === PROBLEM_CODES.tooManyAttempts) {
      return setError(t('tooManyAttempts', { seconds: failure.retryAfterSec ?? 60 }));
    }
    setError(describeProblem(failure, tErrors).title);
  };

  const run = (attempt: PendingLogin, tenantSlug?: string) => {
    setError(null);
    const callbacks = {
      onSuccess: () => router.replace(next),
      onError: (failure: unknown) => handleError(failure, attempt),
    };
    if (attempt.kind === 'google')
      return google.mutate({ idToken: attempt.idToken, tenantSlug }, callbacks);
    if (attempt.kind === 'telegram')
      return telegram.mutate({ ...attempt.payload, tenantSlug }, callbacks);
    const values = form.getValues();
    login.mutate({ email: values.email.trim(), password: values.password, tenantSlug }, callbacks);
  };

  if (tenants.length > 0 && pending) {
    return (
      <div>
        <AuthHeading title={t('chooseSchool')} description={t('chooseSchoolHint')} />
        <ul className="flex flex-col gap-2">
          {tenants.map((tenant) => (
            <li key={tenant.slug}>
              <Button
                variant="secondary"
                className="w-full justify-start"
                loading={login.isPending || google.isPending || telegram.isPending}
                onClick={() => run(pending, tenant.slug)}
              >
                {tenant.name}
              </Button>
            </li>
          ))}
        </ul>
        <Button variant="link" className="mt-4" onClick={() => setTenants([])}>
          {t('back')}
        </Button>
      </div>
    );
  }

  const googleConfig = providers.data?.google;
  const telegramConfig = providers.data?.telegram;
  return (
    <div>
      <AuthHeading title={t('loginTitle')} description={t('loginSubtitle')} />
      {error ? <Alert tone="danger" className="mb-4" title={error} /> : null}
      <form
        noValidate
        onSubmit={form.handleSubmit(() => run({ kind: 'password' }))}
        className="flex flex-col gap-4"
      >
        <Field label={t('email')} error={form.formState.errors.email?.message} required>
          <Input type="email" autoComplete="email" inputMode="email" {...form.register('email')} />
        </Field>
        <Field label={t('password')} error={form.formState.errors.password?.message} required>
          <Input type="password" autoComplete="current-password" {...form.register('password')} />
        </Field>
        <div className="flex justify-end">
          <Link href={ROUTES.forgotPassword} className="text-sm text-primary hover:underline">
            {t('forgotPassword')}
          </Link>
        </div>
        <Button type="submit" size="lg" loading={login.isPending}>
          {t('login')}
        </Button>
      </form>
      {googleConfig || telegramConfig ? (
        <div className="mt-6 flex flex-col gap-3">
          <div className="flex items-center gap-3 text-xs text-text-muted">
            <span className="h-px flex-1 bg-border" />
            {t('or')}
            <span className="h-px flex-1 bg-border" />
          </div>
          {googleConfig ? (
            <GoogleButton
              clientId={googleConfig.clientId}
              onCredential={(idToken) => run({ kind: 'google', idToken })}
            />
          ) : null}
          {telegramConfig ? (
            <TelegramButton
              botUsername={telegramConfig.botUsername}
              onAuth={(payload) => run({ kind: 'telegram', payload })}
            />
          ) : null}
        </div>
      ) : null}
      <p className="mt-6 text-center text-sm text-text-muted">
        {t('noAccount')}{' '}
        <Link href={ROUTES.register} className="font-medium text-primary hover:underline">
          {t('registerLink')}
        </Link>
      </p>
    </div>
  );
}
