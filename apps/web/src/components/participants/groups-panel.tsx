'use client';
import { Plus, Shuffle, Trash2, Users } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/avatar';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Input, NativeSelect } from '@/components/ui/input';
import { Panel } from '@/components/ui/page-header';
import {
  useEnrollmentMutations,
  useEnrollments,
  useGroups,
} from '@/features/enrollment/use-enrollment';
import { flattenPages } from '@/lib/api/pagination';
import type { Group } from '@/lib/api/schemas/enrollment';

function MembersDialog({ courseId, group }: { courseId: string; group: Group }) {
  const t = useTranslations('participants');
  const tCommon = useTranslations('common');
  const [open, setOpen] = useState(false);
  const [members, setMembers] = useState<Set<string>>(new Set(group.memberIds));
  const students = useEnrollments(courseId, { role: 'student' });
  const { setMembers: save } = useEnrollmentMutations(courseId);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) setMembers(new Set(group.memberIds));
      }}
    >
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Users aria-hidden /> {t('members', { count: group.memberIds.length })}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('membersTitle', { group: group.name })} closeLabel={tCommon('close')}>
        <ul className="flex max-h-80 flex-col gap-1 overflow-y-auto">
          {flattenPages(students.data?.pages).map((enrollment) => (
            <li key={enrollment.user.id}>
              <label className="flex items-center gap-2.5 rounded-full px-3 py-1.5 text-sm hover:bg-surface-muted">
                <Checkbox
                  checked={members.has(enrollment.user.id)}
                  onCheckedChange={(checked) =>
                    setMembers((current) => {
                      const next = new Set(current);
                      if (checked) next.add(enrollment.user.id);
                      else next.delete(enrollment.user.id);
                      return next;
                    })
                  }
                />
                <Avatar
                  name={`${enrollment.user.firstName} ${enrollment.user.lastName}`}
                  src={enrollment.user.avatarUrl}
                  size="sm"
                />
                {enrollment.user.firstName} {enrollment.user.lastName}
              </label>
            </li>
          ))}
        </ul>
        <DialogFooter>
          <Button
            loading={save.isPending}
            onClick={() =>
              save.mutate(
                { id: group.id, userIds: [...members] },
                { onSuccess: () => setOpen(false) },
              )
            }
          >
            {tCommon('save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function AutoGroupsDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tCommon = useTranslations('common');
  const { autoGroups } = useEnrollmentMutations(courseId);
  const [open, setOpen] = useState(false);
  const [strategy, setStrategy] = useState<'by_count' | 'by_size'>('by_count');
  const [value, setValue] = useState('2');
  const [prefix, setPrefix] = useState(t('groupPrefixDefault'));
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Shuffle aria-hidden /> {t('autoGroups')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('autoGroups')}
        description={t('autoGroupsHint')}
        closeLabel={tCommon('close')}
      >
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            autoGroups.mutate(
              { strategy, value: Number(value), prefix: prefix || undefined },
              { onSuccess: () => setOpen(false) },
            );
          }}
        >
          <Field label={t('strategy')}>
            <NativeSelect
              value={strategy}
              onChange={(event) => setStrategy(event.target.value as 'by_count' | 'by_size')}
            >
              <option value="by_count">{t('strategies.by_count')}</option>
              <option value="by_size">{t('strategies.by_size')}</option>
            </NativeSelect>
          </Field>
          <Field label={strategy === 'by_count' ? t('groupCount') : t('groupSize')}>
            <Input
              type="number"
              min={1}
              value={value}
              onChange={(event) => setValue(event.target.value)}
            />
          </Field>
          <Field label={t('prefix')}>
            <Input value={prefix} onChange={(event) => setPrefix(event.target.value)} />
          </Field>
          <DialogFooter>
            <Button type="submit" loading={autoGroups.isPending}>
              {t('distribute')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** FR-ENROL-05: groups CRUD, members, auto-distribution. */
export function GroupsPanel({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const groups = useGroups(courseId);
  const mutations = useEnrollmentMutations(courseId);
  const [name, setName] = useState('');
  return (
    <Panel
      title={t('groupsTitle')}
      description={t('groupsHint')}
      actions={<AutoGroupsDialog courseId={courseId} />}
    >
      <form
        className="flex flex-col gap-2 sm:flex-row"
        onSubmit={(event) => {
          event.preventDefault();
          if (name.trim())
            mutations.createGroup.mutate(name.trim(), { onSuccess: () => setName('') });
        }}
      >
        <Input
          aria-label={t('newGroup')}
          placeholder={t('newGroup')}
          value={name}
          onChange={(event) => setName(event.target.value)}
          className="h-10 rounded-full border-transparent bg-surface-muted sm:w-72"
        />
        <Button type="submit" loading={mutations.createGroup.isPending}>
          <Plus aria-hidden /> {t('createGroup')}
        </Button>
      </form>
      {groups.data?.length === 0 ? (
        <EmptyState icon={Users} title={t('noGroupsTitle')} description={t('noGroupsText')} />
      ) : null}
      <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        {groups.data?.map((group) => (
          <li
            key={group.id}
            className="flex items-center gap-3 rounded-md border border-card-border bg-surface p-3 pl-4 shadow-sm transition-shadow duration-fast hover:shadow-md"
          >
            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
              <Users className="size-4" aria-hidden />
            </span>
            <span className="min-w-0 flex-1 font-semibold">
              <InlineEdit
                value={group.name}
                label={t('renameGroup', { name: group.name })}
                onSave={(next) => mutations.renameGroup.mutate({ id: group.id, name: next })}
              />
            </span>
            <MembersDialog courseId={courseId} group={group} />
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t('deleteGroup', { name: group.name })}
              onClick={() => mutations.deleteGroup.mutate(group.id)}
            >
              <Trash2 aria-hidden />
            </Button>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
