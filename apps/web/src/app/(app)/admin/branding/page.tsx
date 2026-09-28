'use client';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Brand } from '@/components/layout/brand';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { ErrorState } from '@/components/ui/error-state';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
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
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <div className="flex flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle>{t('identity')}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
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
            <div className="flex flex-wrap items-end gap-3">
              <Field label={t('primaryColor')} hint={t('primaryColorHint')}>
                <Input
                  value={color}
                  onChange={(event) => setColor(event.target.value)}
                  className="w-32 font-mono"
                />
              </Field>
              <input
                type="color"
                aria-label={t('colorPicker')}
                value={parseHexColor(color) ? color : DEFAULT_BRAND_COLOR}
                onChange={(event) => setColor(event.target.value)}
                className="h-10 w-14 cursor-pointer rounded border border-border bg-surface"
              />
              <Button variant="ghost" size="sm" onClick={() => applyBrandColor(color)}>
                {t('tryInApp')}
              </Button>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('defaults')}</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
          </CardContent>
        </Card>
        <Button className="self-end" onClick={save} loading={update.isPending}>
          {tCommon('save')}
        </Button>
      </div>
      <aside
        aria-label={t('preview')}
        style={previewStyle(color)}
        className="flex flex-col gap-3 self-start rounded-md border border-card-border bg-surface p-4 shadow-sm lg:sticky lg:top-[calc(var(--size-header)+1rem)]"
      >
        <span className="text-xs font-semibold uppercase text-text-muted">{t('preview')}</span>
        <Brand
          name={draft.name}
          logoUrl={logoFileId === tenant.data?.logoFileId ? tenant.data?.branding.logoUrl : null}
        />
        <Button>{t('sampleButton')}</Button>
        <span className="rounded-full bg-primary/10 px-3 py-1 text-center text-sm text-primary">
          {t('sampleBadge')}
        </span>
      </aside>
    </div>
  );
}
