'use client';
import { Save } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { FileById } from '@/components/media/file-preview';
import { useCourseContext } from '@/features/courses/course-context';
import { flattenModules, useOutline } from '@/features/courses/use-outline';
import { useGroups } from '@/features/enrollment/use-enrollment';
import { useFileUpload } from '@/features/files/use-files';
import { useItemPatcher } from '@/features/items/use-item';
import { PERMISSIONS } from '@/lib/access/permissions';
import type { Visibility } from '@/lib/api/schemas/common';
import type {
  ConditionGroup,
  ItemCompletionRule,
  ItemDetail,
  ItemSettings,
} from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '@/lib/utils/time';
import { ConditionsEditor } from './conditions-editor';
import {
  AssignmentSettingsFields,
  CompletionTriggers,
  ForumSettingsFields,
  QuizSettingsFields,
} from './settings-fields';

type Draft = {
  title: string;
  visibility: Visibility;
  publishAt: string | null;
  settings: ItemSettings;
  completionRule: ItemCompletionRule;
  conditions: ConditionGroup | null;
};

function TypeSettings({
  settings,
  onChange,
}: {
  settings: ItemSettings;
  onChange: (settings: ItemSettings) => void;
}) {
  const t = useTranslations('itemSettings');
  const upload = useFileUpload(settings.kind === 'video' ? 'video' : 'content');
  switch (settings.kind) {
    case 'assignment':
      return <AssignmentSettingsFields value={settings} onChange={onChange} />;
    case 'quiz':
      return <QuizSettingsFields value={settings} onChange={onChange} />;
    case 'forum':
      return <ForumSettingsFields value={settings} onChange={onChange} />;
    case 'url':
      return (
        <Field label={t('url')} required>
          <Input
            type="url"
            value={settings.url}
            onChange={(event) => onChange({ ...settings, url: event.target.value })}
          />
        </Field>
      );
    case 'video':
      return (
        <div className="flex flex-col gap-3">
          <Field label={t('embedUrl')} hint={t('embedOrUpload')}>
            <Input
              type="url"
              value={settings.embedUrl ?? ''}
              onChange={(event) => onChange({ ...settings, embedUrl: event.target.value || null })}
            />
          </Field>
          <FileDropzone
            title={settings.fileId ? t('replaceVideo') : t('uploadVideo')}
            browseLabel={t('browse')}
            accept="video/*"
            multiple={false}
            disabled={upload.isUploading}
            onFiles={async ([file]) => {
              const meta = file ? await upload.upload(file) : null;
              if (meta) onChange({ ...settings, fileId: meta.id, embedUrl: null });
            }}
          />
        </div>
      );
    case 'file':
      return (
        <div className="flex flex-col gap-3">
          {settings.fileId ? <FileById fileId={settings.fileId} mode="card" /> : null}
          <FileDropzone
            title={settings.fileId ? t('replaceFile') : t('uploadFile')}
            browseLabel={t('browse')}
            multiple={false}
            disabled={upload.isUploading}
            onFiles={async ([file]) => {
              const meta = file ? await upload.upload(file) : null;
              if (meta) onChange({ ...settings, fileId: meta.id });
            }}
          />
        </div>
      );
    case 'folder':
      return (
        <div className="flex flex-col gap-3">
          <ul className="flex flex-col gap-2">
            {settings.fileIds.map((fileId) => (
              <li key={fileId} className="flex items-center gap-2">
                <div className="flex-1">
                  <FileById fileId={fileId} mode="card" />
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    onChange({
                      ...settings,
                      fileIds: settings.fileIds.filter((id) => id !== fileId),
                    })
                  }
                >
                  {t('removeFile')}
                </Button>
              </li>
            ))}
          </ul>
          <FileDropzone
            title={t('addFiles')}
            browseLabel={t('browse')}
            disabled={upload.isUploading}
            onFiles={async (files) => {
              const uploaded: string[] = [];
              for (const file of files) {
                const meta = await upload.upload(file);
                if (meta) uploaded.push(meta.id);
              }
              onChange({ ...settings, fileIds: [...settings.fileIds, ...uploaded] });
            }}
          />
        </div>
      );
    case 'page':
      return null;
  }
}

/** «Все настройки» of an item: basics, type settings (progressive disclosure), completion and conditions. */
export function ItemSettingsForm({ item }: { item: ItemDetail }) {
  const t = useTranslations('itemSettings');
  const tCommon = useTranslations('common');
  const { course, can } = useCourseContext();
  const outline = useOutline(course.id);
  const groups = useGroups(course.id, can(PERMISSIONS.groupManage));
  const { patch, isSaving } = useItemPatcher(item);
  const [draft, setDraft] = useState<Draft>({
    title: item.title,
    visibility: item.visibility,
    publishAt: item.publishAt,
    settings: item.settings,
    completionRule: item.completionRule,
    conditions: item.conditions,
  });
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const otherItems = flattenModules(outline.data?.modules ?? [])
    .flatMap((module) => module.items)
    .filter((candidate) => candidate.id !== item.id)
    .map((candidate) => ({ id: candidate.id, title: candidate.title }));

  const save = () =>
    patch({
      title: draft.title.trim() || item.title,
      visibility: draft.visibility,
      publishAt: draft.visibility === 'scheduled' ? draft.publishAt : null,
      settings: draft.settings,
      completionRule: draft.completionRule,
      conditions: draft.conditions,
    }).then(() => toast({ tone: 'success', title: t('saved') }));

  return (
    <form
      className="grid grid-cols-1 items-start gap-4 xl:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault();
        save().catch(() => undefined);
      }}
    >
      <Card>
        <CardHeader>
          <CardTitle>{t('basics')}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Field label={t('title')} required>
            <Input value={draft.title} onChange={(event) => set('title', event.target.value)} />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('visibility')}>
              <NativeSelect
                value={draft.visibility}
                onChange={(event) => set('visibility', event.target.value as Visibility)}
              >
                {(['published', 'hidden', 'scheduled'] as const).map((option) => (
                  <option key={option} value={option}>
                    {t(`visibilityOptions.${option}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            {draft.visibility === 'scheduled' ? (
              <Field label={t('publishAt')}>
                <DateTimeInput
                  value={toDateTimeLocalValue(draft.publishAt)}
                  onChange={(event) => set('publishAt', fromDateTimeLocalValue(event.target.value))}
                />
              </Field>
            ) : null}
          </div>
          <TypeSettings
            settings={draft.settings}
            onChange={(settings) => set('settings', settings)}
          />
        </CardContent>
      </Card>
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>{t('completion')}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <Field label={t('completionMode')}>
              <NativeSelect
                value={draft.completionRule.mode}
                onChange={(event) =>
                  set('completionRule', {
                    ...draft.completionRule,
                    mode: event.target.value as ItemCompletionRule['mode'],
                  })
                }
              >
                {(['none', 'manual', 'auto'] as const).map((mode) => (
                  <option key={mode} value={mode}>
                    {t(`completionModes.${mode}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            {draft.completionRule.mode === 'auto' ? (
              <CompletionTriggers
                value={draft.completionRule.on ?? []}
                onChange={(on) =>
                  set('completionRule', {
                    ...draft.completionRule,
                    on: on as ItemCompletionRule['on'],
                  })
                }
              />
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('conditions')}</CardTitle>
          </CardHeader>
          <CardContent>
            <ConditionsEditor
              value={draft.conditions}
              onChange={(conditions) => set('conditions', conditions)}
              items={otherItems}
              groups={(groups.data ?? []).map((group) => ({ id: group.id, title: group.name }))}
            />
          </CardContent>
        </Card>
      </div>
      <div className="glass sticky bottom-[calc(var(--size-bottom-nav)+env(safe-area-inset-bottom)+0.75rem)] z-10 flex justify-end justify-self-end rounded-full border border-card-border p-1.5 shadow-md md:bottom-4 xl:col-span-2">
        <Button type="submit" loading={isSaving}>
          <Save aria-hidden /> {tCommon('save')}
        </Button>
      </div>
    </form>
  );
}
