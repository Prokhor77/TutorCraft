'use client';
import { Plus, SlidersHorizontal, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { useGradebookMutations } from '@/features/gradebook/use-gradebook';
import { AGGREGATIONS, type GradebookSetup } from '@/lib/api/schemas/gradebook';
import { localId } from '@/lib/utils/ids';

type DraftCategory = { key: string; id?: string; name: string; weight: number };
const NEW_CATEGORY_PREFIX = 'new:';
const PERCENT = 100;

/** Grade categories & weights (FR-GRADE-02) with server formula preview and warnings (FR-GRADE-03, AC-7). */
export function GradebookSetupSheet({
  courseId,
  setup,
}: {
  courseId: string;
  setup: GradebookSetup;
}) {
  const t = useTranslations('gradebook');
  const tCommon = useTranslations('common');
  const { saveSetup } = useGradebookMutations(courseId);
  const [open, setOpen] = useState(false);
  const [aggregation, setAggregation] = useState(setup.aggregation);
  const [categories, setCategories] = useState<DraftCategory[]>([]);
  const [assignments, setAssignments] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    setAggregation(setup.aggregation);
    setCategories(
      setup.categories.map((category) => ({
        key: category.id,
        id: category.id,
        name: category.name,
        weight: Math.round(category.weight * PERCENT),
      })),
    );
    setAssignments(
      Object.fromEntries(setup.items.map((item) => [item.gradeItemId, item.categoryId ?? ''])),
    );
  }, [open, setup]);

  const totalWeight = categories.reduce((sum, category) => sum + category.weight, 0);
  const save = () => {
    const keyToId = new Map(categories.map((category) => [category.key, category.id]));
    saveSetup.mutate({
      aggregation,
      categories: categories.map((category) => ({
        id: category.id,
        name: category.name,
        weight: category.weight / PERCENT,
      })),
      items: setup.items.map((item) => {
        const key = assignments[item.gradeItemId] ?? '';
        return {
          gradeItemId: item.gradeItemId,
          categoryId: key ? (keyToId.get(key) ?? null) : null,
        };
      }),
      scaleId: setup.scaleId,
    });
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="secondary" size="sm">
          <SlidersHorizontal aria-hidden /> {t('setup')}
        </Button>
      </SheetTrigger>
      <SheetContent
        title={t('setupTitle')}
        description={t('setupHint')}
        closeLabel={tCommon('close')}
      >
        <div className="flex flex-col gap-5">
          <div className="rounded-md bg-surface-muted p-3 text-sm">
            <p className="font-medium">{t('formula')}</p>
            <p className="mt-1 font-mono text-sm">{saveSetup.data?.formula ?? setup.formula}</p>
          </div>
          {(saveSetup.data?.warnings ?? setup.warnings).map((warning) => (
            <Alert key={warning.code + warning.message} tone="warning" title={warning.message} />
          ))}
          <Field label={t('aggregation')}>
            <NativeSelect
              value={aggregation}
              onChange={(event) =>
                setAggregation(event.target.value as GradebookSetup['aggregation'])
              }
            >
              {AGGREGATIONS.map((option) => (
                <option key={option} value={option}>
                  {t(`aggregations.${option}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('categories')}</legend>
            {categories.map((category, index) => (
              <div key={category.key} className="flex items-center gap-2">
                <Input
                  aria-label={t('categoryName')}
                  value={category.name}
                  onChange={(event) =>
                    setCategories((current) =>
                      current.map((entry, i) =>
                        i === index ? { ...entry, name: event.target.value } : entry,
                      ),
                    )
                  }
                />
                <Input
                  aria-label={t('weightPercent')}
                  type="number"
                  min={0}
                  max={PERCENT}
                  value={category.weight}
                  onChange={(event) =>
                    setCategories((current) =>
                      current.map((entry, i) =>
                        i === index ? { ...entry, weight: Number(event.target.value) } : entry,
                      ),
                    )
                  }
                  className="w-24"
                />
                <span className="text-sm text-text-muted">%</span>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('removeCategory')}
                  onClick={() => setCategories((current) => current.filter((_, i) => i !== index))}
                >
                  <Trash2 aria-hidden />
                </Button>
              </div>
            ))}
            <p
              className={
                totalWeight === PERCENT
                  ? 'text-xs text-text-muted'
                  : 'text-xs font-medium text-warning'
              }
            >
              {t('totalWeight', { total: totalWeight })}
            </p>
            <Button
              variant="secondary"
              size="sm"
              className="self-start"
              onClick={() =>
                setCategories((current) => [
                  ...current,
                  { key: NEW_CATEGORY_PREFIX + localId(), name: '', weight: 0 },
                ])
              }
            >
              <Plus aria-hidden /> {t('addCategory')}
            </Button>
          </fieldset>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('itemCategories')}</legend>
            {setup.items.map((item) => (
              <label key={item.gradeItemId} className="flex items-center gap-2 text-sm">
                <span className="flex-1 truncate">{item.name}</span>
                <NativeSelect
                  className="w-48"
                  value={assignments[item.gradeItemId] ?? ''}
                  onChange={(event) =>
                    setAssignments((current) => ({
                      ...current,
                      [item.gradeItemId]: event.target.value,
                    }))
                  }
                >
                  <option value="">{t('uncategorized')}</option>
                  {categories.map((category) => (
                    <option key={category.key} value={category.key}>
                      {category.name || t('unnamed')}
                    </option>
                  ))}
                </NativeSelect>
              </label>
            ))}
          </fieldset>
          <Button onClick={save} loading={saveSetup.isPending}>
            {tCommon('save')}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
