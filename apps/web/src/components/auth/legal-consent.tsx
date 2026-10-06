'use client';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useId, type ReactNode } from 'react';
import {
  Controller,
  type Control,
  type FieldErrors,
  type FieldValues,
  type Path,
} from 'react-hook-form';
import { z } from 'zod';
import { Checkbox } from '@/components/ui/checkbox';
import { ROUTES } from '@/features/auth/routes';

export type LegalConsentValues = {
  acceptOffer: boolean;
  acceptPrivacy: boolean;
  acceptCrossBorder: boolean;
};

export const LEGAL_CONSENT_DEFAULTS: LegalConsentValues = {
  acceptOffer: false,
  acceptPrivacy: false,
  acceptCrossBorder: false,
};

/**
 * All three boxes must be ticked: accepting the offer, consenting to personal data processing and to its cross-border
 * transfer (Закон РБ № 99-З, ст. 5 и 9).
 */
export function legalConsentSchema(message: string) {
  const ticked = z.boolean().refine(Boolean, message);
  return { acceptOffer: ticked, acceptPrivacy: ticked, acceptCrossBorder: ticked };
}

function DocumentLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link
      href={href}
      target="_blank"
      rel="noopener"
      className="rounded-sm font-semibold text-primary underline-offset-2 hover:underline"
    >
      {children}
    </Link>
  );
}

function ConsentCheckbox<T extends FieldValues>({
  control,
  name,
  error,
  children,
}: {
  control: Control<T>;
  name: Path<T>;
  error?: string;
  children: ReactNode;
}) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <div className="flex flex-col gap-1">
          <div className="flex items-start gap-3">
            <Checkbox
              id={id}
              ref={field.ref}
              name={field.name}
              checked={field.value === true}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              onBlur={field.onBlur}
              aria-required
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
              className="mt-0.5"
            />
            <label htmlFor={id} className="text-sm leading-snug text-text">
              {children}
            </label>
          </div>
          {error ? (
            <p id={errorId} role="alert" className="pl-8 text-xs font-medium text-danger">
              {error}
            </p>
          ) : null}
        </div>
      )}
    />
  );
}

/**
 * Three mandatory consent checkboxes for sign-up forms. The documents open in a new tab so the form keeps its input.
 * The form sends `acceptTerms: true` only after all of them are ticked; core-api refuses sign-up without it and logs the
 * consent with the document edition.
 */
export function LegalConsentFields<T extends FieldValues & LegalConsentValues>({
  control,
  errors,
}: {
  control: Control<T>;
  errors: FieldErrors<T>;
}) {
  const t = useTranslations('legal');
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="sr-only">{t('consentLegend')}</legend>
      <ConsentCheckbox
        control={control}
        name={'acceptOffer' as Path<T>}
        error={errors.acceptOffer?.message as string | undefined}
      >
        {t.rich('acceptOffer', {
          link: (chunks) => <DocumentLink href={ROUTES.offer}>{chunks}</DocumentLink>,
        })}
      </ConsentCheckbox>
      <ConsentCheckbox
        control={control}
        name={'acceptPrivacy' as Path<T>}
        error={errors.acceptPrivacy?.message as string | undefined}
      >
        {t.rich('acceptPrivacy', {
          link: (chunks) => <DocumentLink href={ROUTES.privacy}>{chunks}</DocumentLink>,
        })}
      </ConsentCheckbox>
      <ConsentCheckbox
        control={control}
        name={'acceptCrossBorder' as Path<T>}
        error={errors.acceptCrossBorder?.message as string | undefined}
      >
        {t.rich('acceptCrossBorder', {
          link: (chunks) => <DocumentLink href={ROUTES.crossBorder}>{chunks}</DocumentLink>,
        })}
      </ConsentCheckbox>
    </fieldset>
  );
}

/** Notice under Google/Telegram buttons: those can create an account without the sign-up form. */
export function OAuthConsentNotice() {
  const t = useTranslations('legal');
  return (
    <p className="text-center text-xs text-text-muted">
      {t.rich('oauthNotice', {
        offer: (chunks) => <DocumentLink href={ROUTES.offer}>{chunks}</DocumentLink>,
        privacy: (chunks) => <DocumentLink href={ROUTES.privacy}>{chunks}</DocumentLink>,
        crossBorder: (chunks) => <DocumentLink href={ROUTES.crossBorder}>{chunks}</DocumentLink>,
      })}
    </p>
  );
}
