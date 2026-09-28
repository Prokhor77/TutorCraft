'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { ChevronDown, Plus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { DateTimeInput, Input } from '@/components/ui/input';
import { ROUTES } from '@/features/auth/routes';
import { useCreateCourse } from '@/features/courses/use-courses';
import { applyServerFieldErrors } from '@/features/forms/server-errors';
import { fromDateTimeLocalValue } from '@/lib/utils/time';

const TITLE_MAX = 200;

/** UX-01/02: only the title is required; the rest hides under "More options". */
export function CreateCourseDialog({ trigger }: { trigger?: ReactNode }) {
  const t = useTranslations('courses');
  const tCommon = useTranslations('common');
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const create = useCreateCourse();
  const schema = z.object({
    title: z.string().trim().min(1, t('titleRequired')).max(TITLE_MAX),
    shortName: z.string().trim().optional(),
    startsAt: z.string().optional(),
    endsAt: z.string().optional(),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { title: '', shortName: '', startsAt: '', endsAt: '' },
  });

  const onSubmit = form.handleSubmit((values) =>
    create.mutate(
      {
        title: values.title,
        shortName: values.shortName || undefined,
        startsAt: fromDateTimeLocalValue(values.startsAt ?? '') ?? undefined,
        endsAt: fromDateTimeLocalValue(values.endsAt ?? '') ?? undefined,
      },
      {
        onSuccess: (course) => {
          setOpen(false);
          form.reset();
          router.push(ROUTES.course(course.id));
        },
        onError: (error) =>
          applyServerFieldErrors(error, form.setError, [
            'title',
            'shortName',
            'startsAt',
            'endsAt',
          ]),
      },
    ),
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {trigger ?? (
          <Button>
            <Plus aria-hidden /> {t('create')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent
        title={t('createTitle')}
        description={t('createHint')}
        closeLabel={tCommon('close')}
      >
        <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
          <Field label={t('title')} error={form.formState.errors.title?.message} required>
            <Input autoFocus placeholder={t('titlePlaceholder')} {...form.register('title')} />
          </Field>
          <button
            type="button"
            className="flex items-center gap-1 self-start text-sm font-medium text-primary"
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            <ChevronDown className={expanded ? 'size-4 rotate-180' : 'size-4'} aria-hidden />{' '}
            {tCommon('moreOptions')}
          </button>
          {expanded ? (
            <div className="flex flex-col gap-4">
              <Field label={t('shortName')} hint={t('shortNameHint')}>
                <Input {...form.register('shortName')} />
              </Field>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label={t('startsAt')}>
                  <DateTimeInput {...form.register('startsAt')} />
                </Field>
                <Field label={t('endsAt')}>
                  <DateTimeInput {...form.register('endsAt')} />
                </Field>
              </div>
            </div>
          ) : null}
          <DialogFooter>
            <Button type="submit" loading={create.isPending}>
              {t('createSubmit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
