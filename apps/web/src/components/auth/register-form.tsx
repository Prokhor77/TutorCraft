'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowRight, AtSign, Lock } from 'lucide-react';
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
import { useRegister } from '@/features/auth/use-auth';
import { applyServerFieldErrors } from '@/features/forms/server-errors';
import { AuthHeading } from './auth-heading';

const REGISTER_FIELDS = ['email', 'password', 'firstName', 'lastName', 'schoolName'] as const;
export const PASSWORD_MIN_LENGTH_HINT = 8;
const EMAIL_PARAM = 'email';

/** Tutor sign-up: creates a personal school (tenant) with the user as tenant_admin (ADR-002). */
export function RegisterForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('errors');
  const router = useRouter();
  // Landing «Начать» form sends ?email=… — prefill it (validated by the schema on submit).
  const prefilledEmail = useSearchParams().get(EMAIL_PARAM) ?? '';
  const register = useRegister();
  const [error, setError] = useState<string | null>(null);
  const schema = z.object({
    firstName: z.string().trim().min(1, t('validation.required')),
    lastName: z.string().trim().min(1, t('validation.required')),
    email: z.string().trim().email(t('validation.email')),
    password: z
      .string()
      .min(
        PASSWORD_MIN_LENGTH_HINT,
        t('validation.passwordMin', { min: PASSWORD_MIN_LENGTH_HINT }),
      ),
    schoolName: z.string().trim().optional(),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: '',
      lastName: '',
      email: prefilledEmail,
      password: '',
      schoolName: '',
    },
  });
  const errors = form.formState.errors;

  const onSubmit = form.handleSubmit((values) => {
    setError(null);
    register.mutate(
      { ...values, schoolName: values.schoolName || undefined },
      {
        onSuccess: () => router.replace(ROUTES.courses),
        onError: (failure) => {
          if (!applyServerFieldErrors(failure, form.setError, REGISTER_FIELDS))
            setError(describeProblem(failure, tErrors).title);
        },
      },
    );
  });

  return (
    <div>
      <AuthHeading title={t('registerTitle')} description={t('registerSubtitle')} visuallyHidden />
      {error ? <Alert tone="danger" className="mb-4" title={error} /> : null}
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field
            labelVariant="caps"
            label={t('firstName')}
            error={errors.firstName?.message}
            required
          >
            <Input autoComplete="given-name" {...form.register('firstName')} />
          </Field>
          <Field
            labelVariant="caps"
            label={t('lastName')}
            error={errors.lastName?.message}
            required
          >
            <Input autoComplete="family-name" {...form.register('lastName')} />
          </Field>
        </div>
        <Field labelVariant="caps" label={t('email')} error={errors.email?.message} required>
          <IconInput
            icon={AtSign}
            type="email"
            placeholder={t('emailPlaceholder')}
            autoComplete="email"
            inputMode="email"
            {...form.register('email')}
          />
        </Field>
        <Field
          labelVariant="caps"
          label={t('password')}
          error={errors.password?.message}
          hint={t('passwordHint', { min: PASSWORD_MIN_LENGTH_HINT })}
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
        <Field
          labelVariant="caps"
          label={t('schoolName')}
          hint={t('schoolNameHint')}
          error={errors.schoolName?.message}
        >
          <Input autoComplete="organization" {...form.register('schoolName')} />
        </Field>
        <Button
          type="submit"
          size="lg"
          loading={register.isPending}
          className="mt-2 h-14 text-base"
        >
          {t('register')} <ArrowRight aria-hidden />
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-text-muted">
        {t('haveAccount')}{' '}
        <Link href={ROUTES.login} className="rounded-sm font-semibold text-primary hover:underline">
          {t('loginLink')}
        </Link>
      </p>
    </div>
  );
}
