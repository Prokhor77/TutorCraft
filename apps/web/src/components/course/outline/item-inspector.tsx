'use client';
import {
  Copy,
  ExternalLink,
  Eye,
  MousePointerClick,
  Save,
  Settings2,
  Trash2,
  X,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { flattenModules, useOutline, useOutlineMutations } from '@/features/courses/use-outline';
import { useGroups } from '@/features/enrollment/use-enrollment';
import { useProgressReport } from '@/features/gradebook/use-gradebook';
import { PERMISSIONS } from '@/lib/access/permissions';
import { useUiStore } from '@/stores/ui-store';
import { ConditionsEditor } from '@/components/items/conditions-editor';
import { CompletionTriggers } from '@/components/items/settings-fields';
import { Progress } from '@/components/ui/progress';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useItem, useItemPatcher } from '@/features/items/use-item';
import type { Visibility } from '@/lib/api/schemas/common';
import {
  FORUM_TYPES,
  SUBMISSION_TYPES,
  type ItemCompletionRule,
  type ItemDetail,
  type ItemSettings,
} from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue, SECONDS_PER_MINUTE, toDateTimeLocalValue } from '@/lib/utils/time';
import { ItemTypeIcon } from '../item-meta';

type Draft = {
  title: string;
  visibility: Visibility;
  publishAt: string | null;
  settings: ItemSettings;
  completionRule: ItemCompletionRule;
  conditions: ItemDetail['conditions'];
};

function draftOf(item: ItemDetail): Draft {
  return {
    title: item.title,
    visibility: item.visibility,
    publishAt: item.publishAt,
    settings: item.settings,
    completionRule: item.completionRule,
    conditions: item.conditions,
  };
}

/** Stitch «Статистика»: share of enrolled learners who completed the item (progress report, report.view). */
function ItemStats({ item, courseId }: { item: ItemDetail; courseId: string }) {
  const t = useTranslations('builder');
  const { can } = useCourseContext();
  const enabled = can(PERMISSIONS.reportView) && item.completionRule.mode !== 'none';
  const report = useProgressReport(courseId, enabled);
  if (!enabled || !report.data || report.data.rows.length === 0) return null;
  const total = report.data.rows.length;
  const done = report.data.rows.filter((row) => row.completed.includes(item.id)).length;
  const percent = Math.round((done / total) * PERCENT);
  return (
    <section className="flex flex-col gap-2 rounded bg-surface-muted p-4">
      <h3 className="font-sans text-label-md uppercase text-text-muted">{t('statsTitle')}</h3>
      <p className="flex items-baseline justify-between gap-2 text-sm">
        <span>{t('statsCompleted')}</span>
        <span className="font-semibold text-success">
          {t('statsValue', { percent, done, total })}
        </span>
      </p>
      <Progress value={percent} tone="success" label={t('statsCompleted')} />
    </section>
  );
}

const PERCENT = 100;

function numberOrNull(raw: string): number | null {
  return raw === '' ? null : Number(raw);
}

/** 2–4 key settings per type (UX-01); everything else stays behind «Все настройки». */
function KeySettings({
  settings,
  onChange,
}: {
  settings: ItemSettings;
  onChange: (settings: ItemSettings) => void;
}) {
  const t = useTranslations('itemSettings');
  switch (settings.kind) {
    case 'assignment':
      return (
        <>
          <Field label={t('dueAt')}>
            <DateTimeInput
              value={toDateTimeLocalValue(settings.dueAt)}
              onChange={(event) =>
                onChange({ ...settings, dueAt: fromDateTimeLocalValue(event.target.value) })
              }
            />
          </Field>
          <Field label={t('submissionType')}>
            <NativeSelect
              value={settings.submissionType}
              onChange={(event) =>
                onChange({
                  ...settings,
                  submissionType: event.target.value as typeof settings.submissionType,
                })
              }
            >
              {SUBMISSION_TYPES.map((option) => (
                <option key={option} value={option}>
                  {t(`submissionTypes.${option}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t('maxScore')}>
            <Input
              type="number"
              min={0}
              value={settings.maxScore}
              onChange={(event) => onChange({ ...settings, maxScore: Number(event.target.value) })}
            />
          </Field>
        </>
      );
    case 'quiz':
      return (
        <>
          <Field label={t('closeAt')}>
            <DateTimeInput
              value={toDateTimeLocalValue(settings.closeAt)}
              onChange={(event) =>
                onChange({ ...settings, closeAt: fromDateTimeLocalValue(event.target.value) })
              }
            />
          </Field>
          <Field label={t('timeLimitMinutes')} hint={t('unlimitedHint')}>
            <Input
              type="number"
              min={1}
              value={
                settings.timeLimitSec === null ? '' : settings.timeLimitSec / SECONDS_PER_MINUTE
              }
              onChange={(event) =>
                onChange({
                  ...settings,
                  timeLimitSec: event.target.value
                    ? Number(event.target.value) * SECONDS_PER_MINUTE
                    : null,
                })
              }
            />
          </Field>
          <Field label={t('maxAttempts')} hint={t('unlimitedHint')}>
            <Input
              type="number"
              min={1}
              value={settings.maxAttempts ?? ''}
              onChange={(event) =>
                onChange({ ...settings, maxAttempts: numberOrNull(event.target.value) })
              }
            />
          </Field>
        </>
      );
    case 'forum':
      return (
        <Field label={t('forumType')}>
          <NativeSelect
            value={settings.forumType}
            onChange={(event) =>
              onChange({ ...settings, forumType: event.target.value as typeof settings.forumType })
            }
          >
            {FORUM_TYPES.map((option) => (
              <option key={option} value={option}>
                {t(`forumTypes.${option}`)}
              </option>
            ))}
          </NativeSelect>
        </Field>
      );
    case 'url':
      return (
        <Field label={t('url')}>
          <Input
            type="url"
            value={settings.url}
            onChange={(event) => onChange({ ...settings, url: event.target.value })}
          />
        </Field>
      );
    case 'video':
      return (
        <Field label={t('embedUrl')}>
          <Input
            type="url"
            value={settings.embedUrl ?? ''}
            onChange={(event) => onChange({ ...settings, embedUrl: event.target.value || null })}
          />
        </Field>
      );
    default:
      return null;
  }
}

function InspectorForm({
  item,
  courseId,
  onClose,
}: {
  item: ItemDetail;
  courseId: string;
  onClose?: () => void;
}) {
  const t = useTranslations('builder');
  const tSettings = useTranslations('itemSettings');
  const tTypes = useTranslations('itemTypes');
  const { patch, isSaving } = useItemPatcher(item);
  const { duplicateItem, deleteItem } = useOutlineMutations(courseId);
  const { can } = useCourseContext();
  const outline = useOutline(courseId);
  const groups = useGroups(courseId, can(PERMISSIONS.groupManage));
  const setViewAsStudent = useUiStore((state) => state.setViewAsStudent);
  const otherItems = flattenModules(outline.data?.modules ?? [])
    .flatMap((module) => module.items)
    .filter((candidate) => candidate.id !== item.id)
    .map((candidate) => ({ id: candidate.id, title: candidate.title }));
  const [draft, setDraft] = useState<Draft>(() => draftOf(item));
  useEffect(() => setDraft(draftOf(item)), [item]);
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const dirty = JSON.stringify(draft) !== JSON.stringify(draftOf(item));

  const save = () =>
    patch({
      title: draft.title.trim() || item.title,
      visibility: draft.visibility,
      publishAt: draft.visibility === 'scheduled' ? draft.publishAt : null,
      settings: draft.settings,
      completionRule: draft.completionRule,
      conditions: draft.conditions,
    })
      .then(() => toast({ tone: 'success', title: t('saved') }))
      .catch(() => undefined);

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <div className="flex items-start gap-3">
        <ItemTypeIcon type={item.type} className="size-10 rounded-full" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <Badge tone="primary" className="self-start">
            {tTypes(item.type)}
          </Badge>
          <p className="truncate font-heading text-lg font-semibold">{item.title}</p>
        </div>
        {onClose ? (
          <Button variant="ghost" size="icon-sm" aria-label={t('closeInspector')} onClick={onClose}>
            <X aria-hidden />
          </Button>
        ) : null}
      </div>
      <Tabs defaultValue="settings">
        <TabsList className="w-full">
          <TabsTrigger value="settings" className="flex-1">
            {t('tabSettings')}
          </TabsTrigger>
          <TabsTrigger value="conditions" className="flex-1">
            {t('tabConditions')}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="settings" className="flex flex-col gap-4">
          <Field label={tSettings('title')} required>
            <Input value={draft.title} onChange={(event) => set('title', event.target.value)} />
          </Field>
          <Field label={tSettings('visibility')}>
            <NativeSelect
              value={draft.visibility}
              onChange={(event) => set('visibility', event.target.value as Visibility)}
            >
              {(['published', 'hidden', 'scheduled'] as const).map((option) => (
                <option key={option} value={option}>
                  {tSettings(`visibilityOptions.${option}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          {draft.visibility === 'scheduled' ? (
            <Field label={tSettings('publishAt')}>
              <DateTimeInput
                value={toDateTimeLocalValue(draft.publishAt)}
                onChange={(event) => set('publishAt', fromDateTimeLocalValue(event.target.value))}
              />
            </Field>
          ) : null}
          <KeySettings
            settings={draft.settings}
            onChange={(settings) => set('settings', settings)}
          />
        </TabsContent>
        <TabsContent value="conditions" className="flex flex-col gap-4">
          <Field label={tSettings('completionMode')}>
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
                  {tSettings(`completionModes.${mode}`)}
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
          <div className="flex flex-col gap-2">
            <span className="text-sm font-semibold">{tSettings('conditions')}</span>
            <ConditionsEditor
              value={draft.conditions}
              onChange={(conditions) => set('conditions', conditions)}
              items={otherItems}
              groups={(groups.data ?? []).map((group) => ({ id: group.id, title: group.name }))}
            />
          </div>
        </TabsContent>
      </Tabs>
      <Button type="submit" variant="success" loading={isSaving} disabled={!dirty}>
        <Save aria-hidden /> {t('saveChanges')}
      </Button>
      <div className="grid grid-cols-2 gap-2">
        <Button asChild variant="secondary" size="sm">
          <Link href={ROUTES.item(courseId, item.id)}>
            <ExternalLink aria-hidden /> {t('open')}
          </Link>
        </Button>
        <Button asChild variant="secondary" size="sm">
          <Link href={ROUTES.itemSettings(courseId, item.id)}>
            <Settings2 aria-hidden /> {t('allSettings')}
          </Link>
        </Button>
        <Button variant="ghost" size="sm" onClick={() => duplicateItem.mutate(item.id)}>
          <Copy aria-hidden /> {t('duplicate')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="text-danger hover:bg-danger-soft hover:text-danger"
          onClick={() => {
            deleteItem.mutate({ id: item.id, title: item.title });
            onClose?.();
          }}
        >
          <Trash2 aria-hidden /> {t('delete')}
        </Button>
      </div>
      <Button type="button" variant="secondary" onClick={() => setViewAsStudent(true)}>
        <Eye aria-hidden /> {t('previewAsStudent')}
      </Button>
      <ItemStats item={item} courseId={courseId} />
    </form>
  );
}

/** Inspector (Stitch right pane, 320px): settings of the item selected in the builder. */
export function ItemInspector({
  itemId,
  courseId,
  onClose,
}: {
  itemId: string | null;
  courseId: string;
  onClose?: () => void;
}) {
  const t = useTranslations('builder');
  const tCommon = useTranslations('common');
  const item = useItem(itemId ?? '');
  if (!itemId) {
    return (
      <div className="flex flex-col items-center gap-3 px-2 py-10 text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-accent/10 text-primary">
          <MousePointerClick className="size-6" aria-hidden />
        </span>
        <p className="font-heading text-base font-semibold">{t('inspectorEmptyTitle')}</p>
        <p className="text-sm text-text-muted">{t('inspectorEmptyText')}</p>
      </div>
    );
  }
  if (item.isLoading || !item.data) return <SkeletonList label={tCommon('loading')} rows={4} />;
  return (
    <InspectorForm key={item.data.id} item={item.data} courseId={courseId} onClose={onClose} />
  );
}
