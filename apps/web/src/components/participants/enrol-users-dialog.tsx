'use client';
import { UserPlus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { describeProblem } from '@/features/app/use-problem-toast';
import { useUsers } from '@/features/admin/use-admin';
import { useEnrollmentMutations } from '@/features/enrollment/use-enrollment';
import { flattenPages } from '@/lib/api/pagination';
import { COURSE_ROLES, type CourseRole } from '@/lib/api/schemas/common';

/** FR-ENROL-01: search school users, pick several, choose a role, enrol. */
export function EnrolUsersDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tCommon = useTranslations('common');
  const tErrors = useTranslations('errors');
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [role, setRole] = useState<CourseRole>('student');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const deferredQuery = useDeferredValue(query.trim());
  const users = useUsers({ q: deferredQuery || undefined, status: 'active' }, open);
  const { enrol } = useEnrollmentMutations(courseId);
  const list = flattenPages(users.data?.pages);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary">
          <UserPlus aria-hidden /> {t('enrolUsers')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('enrolTitle')}
        description={t('enrolHint')}
        closeLabel={tCommon('close')}
      >
        <Input
          type="search"
          aria-label={t('search')}
          placeholder={t('search')}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {users.isError ? (
          <Alert tone="warning" title={describeProblem(users.error, tErrors).title} />
        ) : null}
        <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto">
          {list.map((user) => (
            <li key={user.id}>
              <label className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-surface-muted">
                <Checkbox
                  checked={selected.has(user.id)}
                  onCheckedChange={(checked) =>
                    setSelected((current) => {
                      const next = new Set(current);
                      if (checked) next.add(user.id);
                      else next.delete(user.id);
                      return next;
                    })
                  }
                />
                <span className="flex-1">
                  {user.firstName} {user.lastName}
                </span>
                <span className="text-xs text-text-muted">{user.email}</span>
              </label>
            </li>
          ))}
        </ul>
        <Field label={t('role')}>
          <NativeSelect
            value={role}
            onChange={(event) => setRole(event.target.value as CourseRole)}
          >
            {COURSE_ROLES.map((option) => (
              <option key={option} value={option}>
                {tRoles(option)}
              </option>
            ))}
          </NativeSelect>
        </Field>
        <DialogFooter>
          <Button
            disabled={selected.size === 0}
            loading={enrol.isPending}
            onClick={() =>
              enrol.mutate(
                { userIds: [...selected], role },
                {
                  onSuccess: ({ created }) => {
                    toast({ tone: 'success', title: t('enrolled', { count: created }) });
                    setSelected(new Set());
                    setOpen(false);
                  },
                },
              )
            }
          >
            {t('enrolSelected', { count: selected.size })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
