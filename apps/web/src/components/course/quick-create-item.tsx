'use client';
import { Settings2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import { ROUTES } from '@/features/auth/routes';
import {
  QUICK_FIELDS,
  quickSettings,
  type QuickCreateValues,
} from '@/features/courses/item-defaults';
import { useOutlineMutations } from '@/features/courses/use-outline';
import { useFileUpload } from '@/features/files/use-files';
import type { ItemType } from '@/lib/api/schemas/common';
import { FORUM_TYPES, SUBMISSION_TYPES } from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue } from '@/lib/utils/time';

type Props = {
  courseId: string;
  moduleId: string;
  type: ItemType | null;
  position?: number;
  onClose: () => void;
};

/**
 * Quick activity creation (AC-2): title + 2–4 key fields + «Создать» + «Все настройки».
 * Enter in the title submits; the new item appears in the outline without reload.
 */
export function QuickCreateItem({ courseId, moduleId, type, position, onClose }: Props) {
  const t = useTranslations('quickCreate');
  const tTypes = useTranslations('itemTypes');
  const tCommon = useTranslations('common');
  const router = useRouter();
  const { createItem, moveItem } = useOutlineMutations(courseId);
  const [values, setValues] = useState<QuickCreateValues>({ title: '' });
  const [error, setError] = useState<string>();
  const fileUpload = useFileUpload(type === 'video' ? 'video' : 'content');
  const fields = type ? QUICK_FIELDS[type] : [];
  const set = <K extends keyof QuickCreateValues>(key: K, value: QuickCreateValues[K]) =>
    setValues((current) => ({ ...current, [key]: value }));

  const submit = (openSettings: boolean) => {
    if (!type) return;
    const title = values.title.trim();
    if (!title) return setError(t('titleRequired'));
    createItem.mutate(
      { moduleId, input: { type, title, settings: quickSettings(type, values) } },
      {
        onSuccess: (item) => {
          if (position !== undefined) moveItem.mutate({ id: item.id, moduleId, position });
          setValues({ title: '' });
          onClose();
          if (openSettings) router.push(ROUTES.itemSettings(courseId, item.id));
        },
      },
    );
  };

  return (
    <Dialog open={type !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={type ? t('title', { type: tTypes(type) }) : ''}
        description={t('hint')}
        closeLabel={tCommon('close')}
      >
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit(false);
          }}
        >
          <Field label={t('name')} error={error} required>
            <Input
              autoFocus
              value={values.title}
              onChange={(event) => set('title', event.target.value)}
              placeholder={type ? tTypes(`${type}Placeholder`) : ''}
            />
          </Field>
          {fields.includes('dueAt') ? (
            <Field label={t('dueAt')} hint={t('dueAtHint')}>
              <DateTimeInput
                onChange={(event) => set('dueAt', fromDateTimeLocalValue(event.target.value))}
              />
            </Field>
          ) : null}
          {fields.includes('closeAt') ? (
            <Field label={t('closeAt')}>
              <DateTimeInput
                onChange={(event) => set('closeAt', fromDateTimeLocalValue(event.target.value))}
              />
            </Field>
          ) : null}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {fields.includes('submissionType') ? (
              <Field label={t('submissionType')}>
                <NativeSelect
                  defaultValue="file"
                  onChange={(event) =>
                    set('submissionType', event.target.value as QuickCreateValues['submissionType'])
                  }
                >
                  {SUBMISSION_TYPES.map((option) => (
                    <option key={option} value={option}>
                      {t(`submissionTypes.${option}`)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
            ) : null}
            {fields.includes('maxScore') ? (
              <Field label={t('maxScore')}>
                <Input
                  type="number"
                  min={0}
                  inputMode="numeric"
                  placeholder="100"
                  onChange={(event) =>
                    set('maxScore', event.target.value ? Number(event.target.value) : undefined)
                  }
                />
              </Field>
            ) : null}
            {fields.includes('timeLimitMinutes') ? (
              <Field label={t('timeLimit')} hint={t('timeLimitHint')}>
                <Input
                  type="number"
                  min={0}
                  inputMode="numeric"
                  onChange={(event) =>
                    set('timeLimitMinutes', event.target.value ? Number(event.target.value) : null)
                  }
                />
              </Field>
            ) : null}
            {fields.includes('maxAttempts') ? (
              <Field label={t('maxAttempts')} hint={t('unlimitedHint')}>
                <Input
                  type="number"
                  min={1}
                  inputMode="numeric"
                  onChange={(event) =>
                    set('maxAttempts', event.target.value ? Number(event.target.value) : null)
                  }
                />
              </Field>
            ) : null}
          </div>
          {fields.includes('forumType') ? (
            <Field label={t('forumType')}>
              <NativeSelect
                defaultValue="general"
                onChange={(event) =>
                  set('forumType', event.target.value as QuickCreateValues['forumType'])
                }
              >
                {FORUM_TYPES.map((option) => (
                  <option key={option} value={option}>
                    {t(`forumTypes.${option}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
          ) : null}
          {fields.includes('url') ? (
            <Field label={t('url')} required>
              <Input
                type="url"
                inputMode="url"
                placeholder="https://"
                onChange={(event) => set('url', event.target.value)}
              />
            </Field>
          ) : null}
          {fields.includes('embedUrl') ? (
            <Field label={t('embedUrl')} hint={t('embedOrUpload')}>
              <Input
                type="url"
                inputMode="url"
                placeholder="https://"
                onChange={(event) => set('embedUrl', event.target.value)}
              />
            </Field>
          ) : null}
          {fields.includes('fileId') ? (
            <div className="flex flex-col gap-1.5">
              <FileDropzone
                title={values.fileId ? t('fileAttached') : t('dropFile')}
                browseLabel={t('browse')}
                multiple={false}
                accept={type === 'video' ? 'video/*' : undefined}
                disabled={fileUpload.isUploading}
                onFiles={async ([file]) => {
                  if (!file) return;
                  const meta = await fileUpload.upload(file);
                  if (!meta) return;
                  set('fileId', meta.id);
                  if (!values.title) set('title', meta.name.replace(/\.[^.]+$/, ''));
                }}
              />
              {fileUpload.uploads.map((entry) => (
                <p key={entry.name} className="text-xs text-text-muted" aria-live="polite">
                  {t('uploading', { percent: Math.round(entry.progress * 100) })}
                </p>
              ))}
            </div>
          ) : null}
          <DialogFooter className="sm:justify-between">
            <Button
              type="button"
              variant="ghost"
              onClick={() => submit(true)}
              disabled={createItem.isPending}
            >
              <Settings2 aria-hidden /> {t('allSettings')}
            </Button>
            <Button type="submit" loading={createItem.isPending} disabled={fileUpload.isUploading}>
              {t('create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
