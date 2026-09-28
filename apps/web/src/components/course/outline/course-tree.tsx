'use client';
import { CheckCircle2, ChevronRight, Lock } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { moduleProgress, publishedProgress } from '@/features/courses/outline-moves';
import type { OutlineItem, OutlineModule } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { ITEM_TYPE_ICONS } from '../item-meta';

type TreeProps = {
  modules: OutlineModule[];
  activeItemId?: string | null;
  /** Teacher builder: select the item (canvas + inspector). */
  onSelectItem?: (itemId: string) => void;
  /** Learner: navigate to the item page. */
  hrefFor?: (itemId: string) => string;
  /** `responsive` = icon rail on md (Stitch tablet), full cards on xl; `full` = always full. */
  variant?: 'full' | 'responsive';
  /** Author counts published items («3/4»), learner counts completed ones. */
  audience?: 'author' | 'learner';
};

const rowClass =
  'group relative flex w-full items-center gap-2 rounded-full px-3 py-2 text-left text-sm transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20';

function labelClass(variant: TreeProps['variant']) {
  return variant === 'responsive'
    ? 'sr-only xl:not-sr-only xl:min-w-0 xl:flex-1 xl:truncate'
    : 'min-w-0 flex-1 truncate';
}

function containsItem(module: OutlineModule, itemId: string | null | undefined): boolean {
  if (!itemId) return false;
  return (
    module.items.some((item) => item.id === itemId) ||
    module.children.some((child) => containsItem(child, itemId))
  );
}

/** Count chip: emerald when complete, amber when in progress, slate when nothing yet (Stitch tree). */
function CountChip({ done, total, variant }: { done: number; total: number; variant?: string }) {
  if (total === 0) return null;
  const tone =
    done === total
      ? 'bg-success-soft text-success'
      : done > 0
        ? 'bg-warning-soft text-warning'
        : 'bg-draft text-draft-foreground';
  return (
    <span
      className={cn(
        'shrink-0 rounded-full px-2 py-0.5 text-label-sm tabular-nums',
        tone,
        variant === 'responsive' && 'hidden xl:inline',
      )}
    >
      {done}/{total}
    </span>
  );
}

function ItemNode({
  item,
  active,
  variant,
  audience,
  onSelectItem,
  hrefFor,
}: { item: OutlineItem; active: boolean } & Pick<
  TreeProps,
  'variant' | 'audience' | 'onSelectItem' | 'hrefFor'
>) {
  const t = useTranslations('builder');
  const Icon = ITEM_TYPE_ICONS[item.type];
  const locked = !item.availability.available;
  const draft = audience === 'author' && item.visibility !== 'published';
  const content = (
    <>
      {active ? (
        <span
          aria-hidden
          className="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-full bg-primary"
        />
      ) : null}
      <Icon
        className={cn('size-4 shrink-0', active ? 'text-primary' : 'text-text-muted')}
        aria-hidden
      />
      <span className={cn(labelClass(variant), draft && !active && 'text-text-muted')}>
        {item.title}
      </span>
      {draft ? (
        <span
          className={cn(
            'shrink-0 text-label-sm uppercase text-text-muted',
            variant === 'responsive' && 'hidden xl:inline',
          )}
        >
          {t('draft')}
        </span>
      ) : null}
      {item.completion === 'complete' ? (
        <CheckCircle2 className="size-4 shrink-0 text-success" aria-label={t('complete')} />
      ) : null}
      {locked ? (
        <Lock className="size-3.5 shrink-0 text-text-muted" aria-label={t('locked')} />
      ) : null}
    </>
  );
  const className = cn(
    rowClass,
    active ? 'bg-accent/10 font-semibold text-primary' : 'text-text hover:bg-accent/5',
    variant === 'responsive' && 'justify-center xl:justify-start',
  );
  if (onSelectItem) {
    return (
      <button
        type="button"
        title={item.title}
        aria-current={active ? 'true' : undefined}
        onClick={() => onSelectItem(item.id)}
        className={className}
      >
        {content}
      </button>
    );
  }
  if (hrefFor && !locked) {
    return (
      <Link
        href={hrefFor(item.id)}
        title={item.title}
        aria-current={active ? 'page' : undefined}
        className={className}
      >
        {content}
      </Link>
    );
  }
  return (
    <span title={item.title} className={cn(className, 'cursor-default text-text-muted')}>
      {content}
    </span>
  );
}

function ModuleNode({
  module,
  index,
  depth,
  ...props
}: TreeProps & { module: OutlineModule; index: number; depth: number }) {
  const t = useTranslations('builder');
  const [open, setOpen] = useState(true);
  const { done, total } =
    props.audience === 'author' ? publishedProgress([module]) : moduleProgress(module);
  const current = containsItem(module, props.activeItemId);
  const topLevel = depth === 0;
  return (
    <li
      className={cn(
        topLevel &&
          'rounded-md border bg-surface p-1.5 shadow-sm transition-colors duration-fast xl:p-2',
        topLevel && (current ? 'border-accent/40 ring-1 ring-accent/20' : 'border-card-border'),
      )}
    >
      <div
        className={cn(
          rowClass,
          'font-semibold hover:bg-accent/5',
          props.variant === 'responsive' && 'justify-center xl:justify-start',
        )}
      >
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-label={
            open ? t('collapse', { title: module.title }) : t('expand', { title: module.title })
          }
          className={cn(
            '-ml-1 rounded-full p-0.5 text-text-muted hover:text-primary',
            props.variant === 'responsive' && 'hidden xl:block',
          )}
        >
          <ChevronRight
            className={cn('size-4 transition-transform duration-fast', open && 'rotate-90')}
            aria-hidden
          />
        </button>
        <a
          href={`#module-${module.id}`}
          title={module.title}
          className="flex min-w-0 flex-1 items-center gap-2 rounded-full"
        >
          <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-container text-label-md text-primary">
            {topLevel ? index + 1 : '•'}
          </span>
          <span className={labelClass(props.variant)}>{module.title}</span>
        </a>
        <CountChip done={done} total={total} variant={props.variant} />
      </div>
      {open ? (
        <ul
          className={cn(
            'mt-0.5 flex flex-col gap-0.5',
            props.variant === 'responsive' ? 'xl:pl-3' : 'pl-3',
          )}
        >
          {module.items.map((item) => (
            <li key={item.id}>
              <ItemNode
                item={item}
                active={props.activeItemId === item.id}
                variant={props.variant}
                audience={props.audience}
                onSelectItem={props.onSelectItem}
                hrefFor={props.hrefFor}
              />
            </li>
          ))}
          {module.children.map((child, childIndex) => (
            <ModuleNode
              key={child.id}
              {...props}
              module={child}
              index={childIndex}
              depth={depth + 1}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/**
 * Course structure tree (Stitch): one card per module with a count chip, pill item rows with type icons,
 * draft labels, emerald completion checks and an active accent bar.
 */
export function CourseTree(props: TreeProps) {
  const t = useTranslations('builder');
  return (
    <nav aria-label={t('tree')}>
      <ul className="flex flex-col gap-2 xl:gap-3">
        {props.modules.map((module, index) => (
          <ModuleNode key={module.id} {...props} module={module} index={index} depth={0} />
        ))}
      </ul>
    </nav>
  );
}
