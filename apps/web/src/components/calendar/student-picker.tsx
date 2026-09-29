'use client';
import { Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useId, useMemo, useState } from 'react';
import { Checkbox } from '@/components/ui/checkbox';
import { IconInput } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { useLessonStudents } from '@/features/calendar/use-calendar';
import type { LessonStudent } from '@/lib/api/schemas/me';
import { fullName } from '@/lib/utils/format';

function matches(student: LessonStudent, query: string): boolean {
  const needle = query.trim().toLocaleLowerCase();
  if (!needle) return true;
  return `${fullName(student)} ${student.email}`.toLocaleLowerCase().includes(needle);
}

/** Checklist of the course's active students with search and «select all». */
export function StudentPicker({
  courseId,
  value,
  onChange,
  error,
}: {
  courseId: string;
  value: string[];
  onChange: (ids: string[]) => void;
  error?: string;
}) {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const errorId = useId();
  const [query, setQuery] = useState('');
  const students = useLessonStudents(courseId);
  const visible = useMemo(
    () => (students.data ?? []).filter((student) => matches(student, query)),
    [students.data, query],
  );
  const selected = new Set(value);
  const allVisibleSelected = visible.length > 0 && visible.every((s) => selected.has(s.id));

  const toggle = (id: string, checked: boolean) =>
    onChange(checked ? [...value, id] : value.filter((existing) => existing !== id));
  const toggleVisible = (checked: boolean) => {
    const ids = new Set(value);
    visible.forEach((student) => (checked ? ids.add(student.id) : ids.delete(student.id)));
    onChange([...ids]);
  };

  if (students.isLoading) return <Spinner label={tCommon('loading')} />;
  if ((students.data ?? []).length === 0) {
    return <p className="text-sm text-text-muted">{t('studentsEmpty')}</p>;
  }
  return (
    <div className="flex flex-col gap-2">
      <IconInput
        icon={Search}
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={t('studentsSearch')}
        aria-label={t('studentsSearch')}
      />
      <div className="flex items-center justify-between text-label-md text-text-muted">
        <label className="flex items-center gap-2">
          <Checkbox
            checked={allVisibleSelected}
            onCheckedChange={(c) => toggleVisible(c === true)}
          />
          {t('selectAll')}
        </label>
        <span aria-live="polite">{t('studentsSelected', { count: value.length })}</span>
      </div>
      <ul
        className="flex max-h-48 flex-col gap-1 overflow-y-auto rounded-md bg-surface-muted/60 p-2"
        aria-describedby={error ? errorId : undefined}
      >
        {visible.map((student) => (
          <li key={student.id}>
            <label className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-surface">
              <Checkbox
                checked={selected.has(student.id)}
                onCheckedChange={(checked) => toggle(student.id, checked === true)}
              />
              <span className="min-w-0 flex-1 truncate">{fullName(student) || student.email}</span>
            </label>
          </li>
        ))}
      </ul>
      {error ? (
        <p id={errorId} role="alert" className="text-xs font-medium text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
