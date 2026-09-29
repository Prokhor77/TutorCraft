'use client';
import { Settings } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox, Switch } from '@/components/ui/checkbox';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/dialog';
import { Field, Label } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { useUpdateCourse } from '@/features/courses/use-courses';
import { flattenModules, useOutline } from '@/features/courses/use-outline';
import { useFileUpload } from '@/features/files/use-files';
import type { Visibility } from '@/lib/api/schemas/common';
import { GROUP_MODES, type Course } from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '@/lib/utils/time';
import { useCourseContext } from '@/features/courses/course-context';

type Draft = {
  title: string;
  shortName: string;
  startsAt: string;
  endsAt: string;
  visibility: Visibility;
  publishAt: string;
  coverFileId: string | null;
  selfEnrol: Course['selfEnrol'];
  groupMode: Course['groupMode'];
  minFinalPercent: string;
  requiredItemIds: string[];
};

function draftFromCourse(course: Course): Draft {
  return {
    title: course.title,
    shortName: course.shortName ?? '',
    startsAt: toDateTimeLocalValue(course.startsAt),
    endsAt: toDateTimeLocalValue(course.endsAt),
    visibility: course.visibility,
    publishAt: toDateTimeLocalValue(course.publishAt),
    coverFileId: course.coverFileId,
    selfEnrol: course.selfEnrol,
    groupMode: course.groupMode,
    minFinalPercent: course.completionRule.minFinalPercent?.toString() ?? '',
    requiredItemIds: course.completionRule.requiredItemIds,
  };
}

/** Course settings (title, dates, cover, self-enrol, completion rule, group mode). */
type SheetControl = {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  withTrigger?: boolean;
};

export function CourseSettingsSheet({
  open: controlledOpen,
  onOpenChange,
  withTrigger = true,
}: SheetControl = {}) {
  const t = useTranslations('courseSettings');
  const tCommon = useTranslations('common');
  const { course } = useCourseContext();
  const outline = useOutline(course.id);
  const update = useUpdateCourse(course.id);
  const cover = useFileUpload('cover');
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;
  const setOpen = (next: boolean) => {
    setUncontrolledOpen(next);
    onOpenChange?.(next);
  };
  const [draft, setDraft] = useState<Draft>(() => draftFromCourse(course));
  useEffect(() => {
    if (open) setDraft(draftFromCourse(course));
  }, [open, course]);
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const items = flattenModules(outline.data?.modules ?? []).flatMap((module) => module.items);

  const save = async () => {
    const minFinal = draft.minFinalPercent === '' ? null : Number(draft.minFinalPercent);
    await update.mutateAsync({
      version: course.version,
      patch: {
        title: draft.title.trim() || course.title,
        shortName: draft.shortName.trim() || null,
        startsAt: fromDateTimeLocalValue(draft.startsAt),
        endsAt: fromDateTimeLocalValue(draft.endsAt),
        visibility: draft.visibility,
        publishAt:
          draft.visibility === 'scheduled' ? fromDateTimeLocalValue(draft.publishAt) : null,
        coverFileId: draft.coverFileId,
        selfEnrol: draft.selfEnrol,
        groupMode: draft.groupMode,
        completionRule: { requiredItemIds: draft.requiredItemIds, minFinalPercent: minFinal },
      },
    });
    toast({ tone: 'success', title: t('saved') });
    setOpen(false);
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      {withTrigger ? (
        <SheetTrigger asChild>
          <Button variant="secondary" size="sm">
            <Settings aria-hidden /> {t('open')}
          </Button>
        </SheetTrigger>
      ) : null}
      <SheetContent title={t('title')} closeLabel={tCommon('close')}>
        <form
          className="flex flex-col gap-5"
          onSubmit={(event) => {
            event.preventDefault();
            save().catch(() => undefined);
          }}
        >
          <Field label={t('courseTitle')} required>
            <Input value={draft.title} onChange={(event) => set('title', event.target.value)} />
          </Field>
          <Field label={t('shortName')}>
            <Input
              value={draft.shortName}
              onChange={(event) => set('shortName', event.target.value)}
            />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('startsAt')}>
              <DateTimeInput
                value={draft.startsAt}
                onChange={(event) => set('startsAt', event.target.value)}
              />
            </Field>
            <Field label={t('endsAt')}>
              <DateTimeInput
                value={draft.endsAt}
                onChange={(event) => set('endsAt', event.target.value)}
              />
            </Field>
          </div>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('cover')}</legend>
            {course.coverUrl && draft.coverFileId === course.coverFileId ? (
              // eslint-disable-next-line @next/next/no-img-element -- storage URL
              <img
                src={course.coverUrl}
                alt=""
                className="aspect-[16/7] w-full rounded object-cover"
              />
            ) : null}
            <FileDropzone
              title={
                draft.coverFileId && draft.coverFileId !== course.coverFileId
                  ? t('coverUploaded')
                  : t('coverDrop')
              }
              browseLabel={t('browse')}
              accept="image/png,image/jpeg,image/webp,image/gif"
              multiple={false}
              disabled={cover.isUploading}
              onFiles={async ([file]) => {
                const meta = file ? await cover.upload(file) : null;
                if (meta) set('coverFileId', meta.id);
              }}
            />
          </fieldset>
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
                  value={draft.publishAt}
                  onChange={(event) => set('publishAt', event.target.value)}
                />
              </Field>
            ) : null}
          </div>
          <fieldset className="flex flex-col gap-3 rounded-md bg-surface-muted p-4">
            <legend className="px-1 text-sm font-medium">{t('selfEnrol')}</legend>
            <div className="flex items-center justify-between gap-3">
              <Label htmlFor="self-enrol">{t('selfEnrolEnabled')}</Label>
              <Switch
                id="self-enrol"
                checked={draft.selfEnrol.enabled}
                onCheckedChange={(enabled) => set('selfEnrol', { ...draft.selfEnrol, enabled })}
              />
            </div>
            {draft.selfEnrol.enabled ? (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label={t('enrolCode')} hint={t('enrolCodeHint')}>
                  <Input
                    value={draft.selfEnrol.code ?? ''}
                    onChange={(event) =>
                      set('selfEnrol', { ...draft.selfEnrol, code: event.target.value || null })
                    }
                  />
                </Field>
                <Field label={t('maxStudents')}>
                  <Input
                    type="number"
                    min={1}
                    value={draft.selfEnrol.maxStudents ?? ''}
                    onChange={(event) =>
                      set('selfEnrol', {
                        ...draft.selfEnrol,
                        maxStudents: event.target.value ? Number(event.target.value) : null,
                      })
                    }
                  />
                </Field>
                <Field label={t('enrolUntil')}>
                  <DateTimeInput
                    value={toDateTimeLocalValue(draft.selfEnrol.until)}
                    onChange={(event) =>
                      set('selfEnrol', {
                        ...draft.selfEnrol,
                        until: fromDateTimeLocalValue(event.target.value),
                      })
                    }
                  />
                </Field>
              </div>
            ) : null}
          </fieldset>
          <fieldset className="flex flex-col gap-3 rounded-md bg-surface-muted p-4">
            <legend className="px-1 text-sm font-medium">{t('completion')}</legend>
            <Field label={t('minFinalPercent')} hint={t('minFinalHint')}>
              <Input
                type="number"
                min={0}
                max={100}
                value={draft.minFinalPercent}
                onChange={(event) => set('minFinalPercent', event.target.value)}
              />
            </Field>
            <p className="text-sm font-medium">{t('requiredItems')}</p>
            <ul className="flex max-h-48 flex-col gap-2 overflow-y-auto">
              {items.map((item) => (
                <li key={item.id} className="flex items-center gap-2">
                  <Checkbox
                    id={`req-${item.id}`}
                    checked={draft.requiredItemIds.includes(item.id)}
                    onCheckedChange={(checked) =>
                      set(
                        'requiredItemIds',
                        checked
                          ? [...draft.requiredItemIds, item.id]
                          : draft.requiredItemIds.filter((id) => id !== item.id),
                      )
                    }
                  />
                  <Label htmlFor={`req-${item.id}`} className="font-normal">
                    {item.title}
                  </Label>
                </li>
              ))}
            </ul>
          </fieldset>
          <Field label={t('groupMode')}>
            <NativeSelect
              value={draft.groupMode}
              onChange={(event) => set('groupMode', event.target.value as Course['groupMode'])}
            >
              {GROUP_MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {t(`groupModes.${mode}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Button type="submit" loading={update.isPending}>
            {tCommon('save')}
          </Button>
        </form>
      </SheetContent>
    </Sheet>
  );
}
