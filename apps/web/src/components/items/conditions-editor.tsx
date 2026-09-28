'use client';
import { Plus, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/field';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import type { Condition, ConditionGroup } from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '@/lib/utils/time';

type Option = { id: string; title: string };
type Props = {
  value: ConditionGroup | null;
  onChange: (value: ConditionGroup | null) => void;
  items: Option[];
  groups: Option[];
};

const CONDITION_TYPES = ['date', 'completion', 'grade', 'group'] as const;

function defaultCondition(type: Condition['type'], items: Option[], groups: Option[]): Condition {
  switch (type) {
    case 'date':
      return { type };
    case 'completion':
      return { type, itemId: items[0]?.id ?? '', state: 'complete' };
    case 'grade':
      return { type, itemId: items[0]?.id ?? '', minPercent: 60 };
    case 'group':
      return { type, groupId: groups[0]?.id ?? '' };
  }
}

/** Access conditions (FR-PROG-02): list with all/any, locked display mode (FR-PROG-03). */
export function ConditionsEditor({ value, onChange, items, groups }: Props) {
  const t = useTranslations('conditions');
  const group: ConditionGroup = value ?? { op: 'all', showWhenLocked: true, conditions: [] };
  const setConditions = (conditions: Condition[]) =>
    onChange(conditions.length ? { ...group, conditions } : null);
  const update = (index: number, next: Condition) =>
    setConditions(group.conditions.map((condition, i) => (i === index ? next : condition)));

  return (
    <div className="flex flex-col gap-3">
      {group.conditions.length > 1 ? (
        <NativeSelect
          aria-label={t('operator')}
          value={group.op}
          onChange={(event) =>
            onChange({ ...group, op: event.target.value as ConditionGroup['op'] })
          }
          className="w-auto"
        >
          <option value="all">{t('all')}</option>
          <option value="any">{t('any')}</option>
        </NativeSelect>
      ) : null}
      <ul className="flex flex-col gap-2">
        {group.conditions.map((condition, index) => (
          <li
            key={index}
            className="flex flex-wrap items-end gap-2 rounded-md bg-surface-muted p-3"
          >
            <NativeSelect
              aria-label={t('type')}
              value={condition.type}
              onChange={(event) =>
                update(
                  index,
                  defaultCondition(event.target.value as Condition['type'], items, groups),
                )
              }
              className="w-40"
            >
              {CONDITION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`types.${type}`)}
                </option>
              ))}
            </NativeSelect>
            {condition.type === 'date' ? (
              <>
                <label className="flex flex-col gap-1 text-xs">
                  {t('from')}
                  <DateTimeInput
                    value={toDateTimeLocalValue(condition.from)}
                    onChange={(event) =>
                      update(index, {
                        ...condition,
                        from: fromDateTimeLocalValue(event.target.value) ?? undefined,
                      })
                    }
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs">
                  {t('until')}
                  <DateTimeInput
                    value={toDateTimeLocalValue(condition.until)}
                    onChange={(event) =>
                      update(index, {
                        ...condition,
                        until: fromDateTimeLocalValue(event.target.value) ?? undefined,
                      })
                    }
                  />
                </label>
              </>
            ) : null}
            {condition.type === 'completion' || condition.type === 'grade' ? (
              <NativeSelect
                aria-label={t('item')}
                value={condition.itemId}
                onChange={(event) => update(index, { ...condition, itemId: event.target.value })}
                className="min-w-48 flex-1"
              >
                {items.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.title}
                  </option>
                ))}
              </NativeSelect>
            ) : null}
            {condition.type === 'completion' ? (
              <NativeSelect
                aria-label={t('state')}
                value={condition.state}
                onChange={(event) =>
                  update(index, {
                    ...condition,
                    state: event.target.value as 'complete' | 'incomplete',
                  })
                }
                className="w-40"
              >
                <option value="complete">{t('complete')}</option>
                <option value="incomplete">{t('incomplete')}</option>
              </NativeSelect>
            ) : null}
            {condition.type === 'grade' ? (
              <>
                <label className="flex flex-col gap-1 text-xs">
                  {t('minPercent')}
                  <Input
                    type="number"
                    min={0}
                    max={100}
                    className="w-24"
                    value={condition.minPercent ?? ''}
                    onChange={(event) =>
                      update(index, {
                        ...condition,
                        minPercent: event.target.value ? Number(event.target.value) : undefined,
                      })
                    }
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs">
                  {t('maxPercent')}
                  <Input
                    type="number"
                    min={0}
                    max={100}
                    className="w-24"
                    value={condition.maxPercent ?? ''}
                    onChange={(event) =>
                      update(index, {
                        ...condition,
                        maxPercent: event.target.value ? Number(event.target.value) : undefined,
                      })
                    }
                  />
                </label>
              </>
            ) : null}
            {condition.type === 'group' ? (
              <NativeSelect
                aria-label={t('group')}
                value={condition.groupId}
                onChange={(event) => update(index, { ...condition, groupId: event.target.value })}
                className="min-w-48 flex-1"
              >
                {groups.map((entry) => (
                  <option key={entry.id} value={entry.id}>
                    {entry.title}
                  </option>
                ))}
              </NativeSelect>
            ) : null}
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t('remove')}
              onClick={() => setConditions(group.conditions.filter((_, i) => i !== index))}
            >
              <Trash2 aria-hidden />
            </Button>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          variant="secondary"
          size="sm"
          onClick={() =>
            setConditions([...group.conditions, defaultCondition('date', items, groups)])
          }
        >
          <Plus aria-hidden /> {t('add')}
        </Button>
        {group.conditions.length > 0 ? (
          <div className="flex items-center gap-2">
            <Switch
              id="show-locked"
              checked={group.showWhenLocked}
              onCheckedChange={(showWhenLocked) => onChange({ ...group, showWhenLocked })}
            />
            <Label htmlFor="show-locked">{t('showWhenLocked')}</Label>
          </div>
        ) : null}
      </div>
    </div>
  );
}
