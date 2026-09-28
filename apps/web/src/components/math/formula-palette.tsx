'use client';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { MathView } from '@/components/blockdoc/math-view';
import { Segmented } from '@/components/ui/segmented';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  LIBRARY_GROUPS,
  localize,
  PALETTE_GROUPS,
  searchFormulas,
  toPreviewLatex,
  type FormulaEntry,
  type FormulaGroup,
} from '@/lib/math/formula-catalog';

type PickHandler = (latex: string) => void;

const PALETTE_TAB = 'templates';
const LIBRARY_TAB = 'library';
const RECENT_TAB = 'recent';

function TemplateTile({ entry, onPick }: { entry: FormulaEntry; onPick: PickHandler }) {
  const locale = useLocale();
  const label = localize(entry.label, locale);
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={() => onPick(entry.latex)}
      className="flex h-12 min-w-12 items-center justify-center rounded border-[1.5px] border-border bg-surface px-2 text-base transition-colors duration-fast hover:border-accent hover:bg-primary-soft/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
    >
      <MathView latex={toPreviewLatex(entry.latex)} block={false} className="pointer-events-none" />
    </button>
  );
}

function FormulaRow({
  latex,
  label,
  onPick,
}: {
  latex: string;
  label?: string;
  onPick: PickHandler;
}) {
  return (
    <button
      type="button"
      onClick={() => onPick(latex)}
      aria-label={label ?? latex}
      className="flex min-h-14 w-full flex-col items-start gap-1 overflow-hidden rounded border-[1.5px] border-border bg-surface px-3 py-2 text-left transition-colors duration-fast hover:border-accent hover:bg-primary-soft/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
    >
      <MathView latex={toPreviewLatex(latex)} block={false} className="pointer-events-none" />
      {label ? <span className="text-xs text-text-muted">{label}</span> : null}
    </button>
  );
}

function GroupedTiles({
  groups,
  label,
  variant,
  onPick,
}: {
  groups: readonly FormulaGroup[];
  label: string;
  variant: 'tile' | 'row';
  onPick: PickHandler;
}) {
  const locale = useLocale();
  const [groupId, setGroupId] = useState(groups[0]?.id ?? '');
  const active = groups.find((group) => group.id === groupId) ?? groups[0];
  return (
    <div className="flex flex-col gap-3">
      <Segmented
        label={label}
        value={groupId}
        onChange={setGroupId}
        options={groups.map((group) => ({ value: group.id, label: localize(group.title, locale) }))}
      />
      {variant === 'tile' ? (
        <div className="flex flex-wrap gap-1.5">
          {active?.items.map((entry) => (
            <TemplateTile key={entry.latex} entry={entry} onPick={onPick} />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {active?.items.map((entry) => (
            <FormulaRow
              key={entry.latex}
              latex={entry.latex}
              label={localize(entry.label, locale)}
              onPick={onPick}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SearchResults({ query, onPick }: { query: string; onPick: PickHandler }) {
  const t = useTranslations('math');
  const locale = useLocale();
  const templates = useMemo(() => searchFormulas(PALETTE_GROUPS, query), [query]);
  const formulas = useMemo(() => searchFormulas(LIBRARY_GROUPS, query), [query]);
  if (templates.length === 0 && formulas.length === 0)
    return <p className="py-6 text-center text-sm text-text-muted">{t('searchEmpty')}</p>;
  return (
    <div className="flex flex-col gap-3" aria-live="polite">
      {templates.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {templates.map((entry) => (
            <TemplateTile key={entry.latex} entry={entry} onPick={onPick} />
          ))}
        </div>
      ) : null}
      {formulas.length > 0 ? (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {formulas.map((entry) => (
            <FormulaRow
              key={entry.latex}
              latex={entry.latex}
              label={localize(entry.label, locale)}
              onPick={onPick}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** Templates, ready formulas and recent ones; a non-empty search replaces the tabs. */
export function FormulaPalette({
  query,
  recent,
  onPick,
}: {
  query: string;
  recent: readonly string[];
  onPick: PickHandler;
}) {
  const t = useTranslations('math');
  if (query.trim()) return <SearchResults query={query} onPick={onPick} />;
  return (
    <Tabs defaultValue={PALETTE_TAB}>
      <TabsList>
        <TabsTrigger value={PALETTE_TAB}>{t('tabs.templates')}</TabsTrigger>
        <TabsTrigger value={LIBRARY_TAB}>{t('tabs.library')}</TabsTrigger>
        <TabsTrigger value={RECENT_TAB}>{t('tabs.recent')}</TabsTrigger>
      </TabsList>
      <TabsContent value={PALETTE_TAB}>
        <GroupedTiles groups={PALETTE_GROUPS} label={t('section')} variant="tile" onPick={onPick} />
      </TabsContent>
      <TabsContent value={LIBRARY_TAB}>
        <GroupedTiles groups={LIBRARY_GROUPS} label={t('section')} variant="row" onPick={onPick} />
      </TabsContent>
      <TabsContent value={RECENT_TAB}>
        {recent.length === 0 ? (
          <p className="py-6 text-center text-sm text-text-muted">{t('recentEmpty')}</p>
        ) : (
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {recent.map((latex) => (
              <FormulaRow key={latex} latex={latex} onPick={onPick} />
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  );
}
