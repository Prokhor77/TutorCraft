'use client';
import { Save, Wand2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Brand } from '@/components/layout/brand';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { ErrorState } from '@/components/ui/error-state';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { useTenantSettings, useUpdateTenant } from '@/features/admin/use-admin';
import { useFileUpload } from '@/features/files/use-files';
import { LOCALES } from '@/lib/api/schemas/auth';
import type { TenantSettings } from '@/lib/api/schemas/org';
import { applyBrandColor, parseHexColor, readableForeground, toChannels } from '@/lib/utils/color';
import { useAuthStore } from '@/stores/auth-store';

const DEFAULT_BRAND_COLOR = '#4f46e5';

function previewStyle(color: string): React.CSSProperties | undefined {
  const rgb = parseHexColor(color);
  if (!rgb) return undefined;
  return {
    ['--primary' as string]: toChannels(rgb),
    ['--primary-foreground' as string]: toChannels(readableForeground(rgb)),
    ['--accent' as string]: toChannels(rgb),
  };
}

/** FR-ADMIN-01: name, logo, primary color with live preview, locale/timezone, password policy, embed whitelist. */
export default function AdminBrandingPage() {
  const t = useTranslations('adminBranding');
  const tCommon = useTranslations('common');
  const tenant = useTenantSettings();
  const update = useUpdateTenant();
  const logo = useFileUpload('cover');
  const [draft, setDraft] = useState<TenantSettings | null>(null);
  const [color, setColor] = useState(DEFAULT_BRAND_COLOR);
  const [logoFileId, setLogoFileId] = useState<string | null>(null);
  useEffect(() => {
    if (!tenant.data) return;
    setDraft(tenant.data);
    setColor(tenant.data.branding.primaryColor ?? DEFAULT_BRAND_COLOR);
    setLogoFileId(tenant.data.logoFileId);
  }, [tenant.data]);
  useEffect(
    () => () => applyBrandColor(useAuthStore.getState().me?.tenant.branding.primaryColor ?? null),
    [],
  );

  if (tenant.isLoading || !draft)
    return tenant.isError ? (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void tenant.refetch()}
      />
    ) : (
      <SkeletonList label={tCommon('loading')} />
    );
  const set = <K extends keyof TenantSettings>(key: K, value: TenantSettings[K]) =>
    setDraft((current) => (current ? { ...current, [key]: value } : current));

  const save = () =>
    update.mutate(
      {
        version: draft.version,
        name: draft.name,
        logoFileId,
        primaryColor: parseHexColor(color) ? color : null,
        defaultLocale: draft.defaultLocale,
        defaultTimezone: draft.defaultTimezone,
        passwordPolicy: draft.passwordPolicy,
        embedWhitelist: draft.embedWhitelist,
      },
      {
        onSuccess: (saved) => {
          applyBrandColor(saved.branding.primaryColor);
          toast({ tone: 'success', title: t('saved') });
        },
      },
    );

  return (
    <div className="grid grid-cols-1 gap-gutter lg:grid-cols-[minmax(0,1fr)_20rem]">
      <div className="flex flex-col gap-gutter">
        <Panel title={t('identity')} description={t('identityHint')}>
          <Field label={t('name')} required>
            <Input value={draft.name} onChange={(event) => set('name', event.target.value)} />
          </Field>
          <FileDropzone
            title={logoFileId !== tenant.data?.logoFileId ? t('logoUploaded') : t('logo')}
            browseLabel={t('browse')}
            accept="image/png,image/jpeg,image/webp,image/gif"
            multiple={false}
            disabled={logo.isUploading}
            onFiles={async ([file]) => {
              const meta = file ? await logo.upload(file) : null;
              if (meta) setLogoFileId(meta.id);
            }}
          />
          <div className="flex flex-wrap items-start gap-3 rounded-md bg-surface-muted p-4">
            <input
              type="color"
              aria-label={t('colorPicker')}
              value={parseHexColor(color) ? color : DEFAULT_BRAND_COLOR}
              onChange={(event) => setColor(event.target.value)}
              className="mt-6 size-11 shrink-0 cursor-pointer appearance-none rounded-full border-2 border-surface bg-transparent p-0 shadow-sm [&::-moz-color-swatch]:rounded-full [&::-moz-color-swatch]:border-0 [&::-webkit-color-swatch-wrapper]:p-0 [&::-webkit-color-swatch]:rounded-full [&::-webkit-color-swatch]:border-0"
            />
            <Field label={t('primaryColor')} hint={t('primaryColorHint')}>
              <Input
                value={color}
                onChange={(event) => setColor(event.target.value)}
                className="w-36 font-mono uppercase"
              />
            </Field>
            <Button
              variant="secondary"
              size="sm"
              className="mt-6 sm:ml-auto"
              onClick={() => applyBrandColor(color)}
            >
              <Wand2 aria-hidden /> {t('tryInApp')}
            </Button>
          </div>
        </Panel>
        <Panel title={t('defaults')} description={t('defaultsHint')}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('defaultLocale')}>
              <NativeSelect
                value={draft.defaultLocale}
                onChange={(event) =>
                  set('defaultLocale', event.target.value as TenantSettings['defaultLocale'])
                }
              >
                {LOCALES.map((locale) => (
                  <option key={locale} value={locale}>
                    {locale.toUpperCase()}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label={t('defaultTimezone')}>
              <Input
                value={draft.defaultTimezone}
                onChange={(event) => set('defaultTimezone', event.target.value)}
              />
            </Field>
            <Field label={t('passwordMin')}>
              <Input
                type="number"
                min={6}
                value={draft.passwordPolicy.minLength}
                onChange={(event) =>
                  set('passwordPolicy', {
                    ...draft.passwordPolicy,
                    minLength: Number(event.target.value),
                  })
                }
              />
            </Field>
            <div className="flex flex-col justify-end gap-2 text-sm">
              <label className="flex items-center gap-2">
                <Checkbox
                  checked={draft.passwordPolicy.requireDigit}
                  onCheckedChange={(checked) =>
                    set('passwordPolicy', {
                      ...draft.passwordPolicy,
                      requireDigit: checked === true,
                    })
                  }
                />
                {t('requireDigit')}
              </label>
              <label className="flex items-center gap-2">
                <Checkbox
                  checked={draft.passwordPolicy.requireLetter}
                  onCheckedChange={(checked) =>
                    set('passwordPolicy', {
                      ...draft.passwordPolicy,
                      requireLetter: checked === true,
                    })
                  }
                />
                {t('requireLetter')}
              </label>
            </div>
            <Field
              label={t('embedWhitelist')}
              hint={t('embedWhitelistHint')}
              className="sm:col-span-2"
            >
              <Textarea
                value={draft.embedWhitelist.join('\n')}
                onChange={(event) =>
                  set('embedWhitelist', event.target.value.split(/\s+/).filter(Boolean))
                }
              />
            </Field>
          </div>
        </Panel>
        <div className="flex justify-end">
          <Button variant="success" onClick={save} loading={update.isPending}>
            <Save aria-hidden /> {tCommon('save')}
          </Button>
        </div>
      </div>
      <aside
        aria-label={t('preview')}
        style={previewStyle(color)}
        className="flex flex-col gap-4 self-start rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-6 lg:sticky lg:top-[calc(var(--size-header)+1rem)]"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg">{t('preview')}</h2>
          <span className="flex items-center gap-1.5 rounded-full bg-surface-muted px-2.5 py-1 font-mono text-label-md text-text-muted">
            <span className="size-3 rounded-full bg-primary" aria-hidden />
            {parseHexColor(color) ? color.toUpperCase() : '—'}
          </span>
        </div>
        <div
          inert
          aria-hidden
          className="flex flex-col gap-4 rounded-md border border-border bg-background p-4"
        >
          <Brand
            name={draft.name}
            logoUrl={logoFileId === tenant.data?.logoFileId ? tenant.data?.branding.logoUrl : null}
          />
          <span className="inline-flex gap-1 self-start rounded-full bg-surface-muted p-1">
            <span className="rounded-full bg-primary px-3 py-1 text-label-md text-primary-foreground">
              {t('sampleTabActive')}
            </span>
            <span className="rounded-full px-3 py-1 text-label-md text-text-muted">
              {t('sampleTab')}
            </span>
          </span>
          <div className="flex flex-col gap-3 rounded-md bg-surface p-4 shadow-sm">
            <span className="self-start rounded-full bg-primary/10 px-2.5 py-0.5 text-label-md text-primary">
              {t('sampleBadge')}
            </span>
            <span className="h-2 overflow-hidden rounded-full bg-surface-container">
              <span className="block h-full w-2/3 rounded-full bg-primary" />
            </span>
            <Button tabIndex={-1}>{t('sampleButton')}</Button>
            <Button tabIndex={-1} variant="secondary">
              {t('sampleSecondary')}
            </Button>
          </div>
        </div>
        <p className="text-xs text-text-muted">{t('previewHint')}</p>
      </aside>
    </div>
  );
}
