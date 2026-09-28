'use client';
import {
  Check,
  ExternalLink,
  FileText,
  FolderOpen,
  Images,
  LayoutList,
  PencilLine,
  Play,
  Search,
  Trash2,
  UploadCloud,
  Video,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useDeferredValue, useRef, useState, type ReactNode } from 'react';
import { ITEM_TYPE_ICONS } from '@/components/course/item-meta';
import { StatusChip } from '@/components/course/status-chip';
import { ResourceView } from '@/components/items/resource-views';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/checkbox';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Input, NativeSelect } from '@/components/ui/input';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { toast } from '@/components/ui/toast';
import { BREAKPOINTS, useMediaQuery } from '@/features/app/use-media-query';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { flattenModules, useOutline, useOutlineMutations } from '@/features/courses/use-outline';
import { useFileUpload } from '@/features/files/use-files';
import { useItem, useItemPatcher } from '@/features/items/use-item';
import { PERMISSIONS } from '@/lib/access/permissions';
import type { ItemType } from '@/lib/api/schemas/common';
import type { ItemDetail, OutlineItem } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';

/** Material types shown in the media library (resources, not activities). */
export const MEDIA_TYPES = [
  'video',
  'file',
  'folder',
  'page',
  'url',
] as const satisfies readonly ItemType[];
type MediaType = (typeof MEDIA_TYPES)[number];
type MediaEntry = OutlineItem & { moduleId: string; moduleTitle: string };
type Filters = { type: MediaType | null; moduleId: string | null; draftsOnly: boolean };
const NO_FILTERS: Filters = { type: null, moduleId: null, draftsOnly: false };
const FILE_EXTENSION = /\.[^.]+$/;
const PERCENT = 100;
const UPLOAD_ACCEPT = 'video/*,audio/*,application/pdf,image/*,.doc,.docx,.ppt,.pptx,.xls,.xlsx';

/** Stitch colour-codes material types (violet video, red notes, amber folders, emerald pages, info links). */
const TYPE_TONE: Record<MediaType, BadgeTone> = {
  video: 'primary',
  file: 'danger',
  folder: 'warning',
  page: 'success',
  url: 'info',
};
const TONE_CHIP: Record<BadgeTone, string> = {
  primary: 'bg-primary-soft text-primary',
  danger: 'bg-danger-soft text-danger',
  warning: 'bg-warning-soft text-warning',
  success: 'bg-success-soft text-success',
  info: 'bg-info-soft text-info',
  neutral: 'bg-draft text-draft-foreground',
};

function MediaCard({
  entry,
  active,
  onSelect,
}: {
  entry: MediaEntry;
  active: boolean;
  onSelect: () => void;
}) {
  const tTypes = useTranslations('itemTypes');
  const type = entry.type as MediaType;
  const tone = TYPE_TONE[type];
  const Icon = ITEM_TYPE_ICONS[type];
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={active}
      className={cn(
        'lift relative flex h-full w-full flex-col gap-2 rounded-lg border-2 bg-surface p-1.5 text-left shadow-sm transition-[box-shadow,border-color] duration-fast hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:gap-3 sm:p-2',
        active ? 'border-primary shadow-md' : 'border-transparent hover:border-card-border-hover',
      )}
    >
      <span
        className={cn(
          'relative flex aspect-[4/3] w-full items-center justify-center overflow-hidden rounded-md sm:aspect-video',
          TONE_CHIP[tone],
        )}
      >
        <span
          className="absolute inset-0 bg-gradient-to-br from-surface/60 via-transparent to-surface/40"
          aria-hidden
        />
        {type === 'video' ? (
          <span className="relative flex size-12 items-center justify-center rounded-full bg-surface/90 text-primary shadow-md">
            <Play className="size-5 translate-x-px fill-current" aria-hidden />
          </span>
        ) : (
          <Icon className="relative size-10" aria-hidden />
        )}
        <span className="absolute bottom-2 left-2 rounded-full bg-text/80 px-2 py-0.5 text-label-sm uppercase text-surface">
          {tTypes(type)}
        </span>
        {active ? (
          <span className="absolute right-2 top-2 flex size-6 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm">
            <Check className="size-3.5" aria-hidden />
          </span>
        ) : null}
      </span>
      <span className="flex flex-1 flex-col gap-2 px-1.5 pb-1.5 sm:px-2.5 sm:pb-2">
        <StatusChip visibility={entry.visibility} className="self-start" />
        <span className="line-clamp-2 font-heading text-sm font-semibold sm:text-base">
          {entry.title}
        </span>
        <span className="mt-auto flex items-center gap-1.5 border-t border-border pt-2 text-xs text-primary">
          <LayoutList className="size-3.5 shrink-0" aria-hidden />
          <span className="truncate">{entry.moduleTitle}</span>
        </span>
      </span>
    </button>
  );
}

function InspectorSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 border-t border-border pt-4">
      <h3 className="font-sans text-label-md uppercase text-text-muted">{title}</h3>
      {children}
    </section>
  );
}

function InspectorBody({
  item,
  moduleTitle,
  canEdit,
  onDeleted,
}: {
  item: ItemDetail;
  moduleTitle?: string;
  canEdit: boolean;
  onDeleted: () => void;
}) {
  const t = useTranslations('media');
  const tTypes = useTranslations('itemTypes');
  const { patch } = useItemPatcher(item);
  const { deleteItem } = useOutlineMutations(item.courseId);
  const published = item.visibility === 'published';
  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-hidden rounded-md">
        <ResourceView item={item} />
      </div>
      <div className="flex flex-col gap-1">
        <span className="flex items-center justify-between gap-2 text-label-md uppercase text-text-muted">
          {t('fileName')}
          {canEdit ? <PencilLine className="size-3.5" aria-hidden /> : null}
        </span>
        <p className="font-heading text-lg font-semibold">
          <InlineEdit
            value={item.title}
            label={t('rename', { title: item.title })}
            disabled={!canEdit}
            onSave={(title) => void patch({ title }).catch(() => undefined)}
          />
        </p>
      </div>
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 rounded-md bg-surface-muted p-4 text-xs">
        <dt className="text-text-muted">{t('metaType')}</dt>
        <dd className="justify-self-end font-semibold">{tTypes(item.type)}</dd>
        <dt className="text-text-muted">{t('metaModule')}</dt>
        <dd className="justify-self-end truncate font-semibold">{moduleTitle ?? '—'}</dd>
        <dt className="self-center text-text-muted">{t('metaStatus')}</dt>
        <dd className="justify-self-end">
          <StatusChip visibility={item.visibility} />
        </dd>
      </dl>
      <InspectorSection title={t('courseLink')}>
        <span className="flex items-center gap-2 rounded-full bg-primary-soft px-3.5 py-2 text-sm text-primary">
          <LayoutList className="size-4 shrink-0" aria-hidden />
          <span className="truncate">{moduleTitle ?? '—'}</span>
        </span>
      </InspectorSection>
      {canEdit ? (
        <InspectorSection title={t('access')}>
          <div className="flex items-center justify-between gap-3">
            <span className="flex flex-col">
              <span className="text-sm font-semibold">{t('visibleToStudents')}</span>
              <span className="text-xs text-text-muted">{t('visibleHint')}</span>
            </span>
            <Switch
              checked={published}
              aria-label={t('visibleToStudents')}
              onCheckedChange={(on) =>
                void patch({ visibility: on ? 'published' : 'hidden' }).catch(() => undefined)
              }
            />
          </div>
        </InspectorSection>
      ) : null}
      <div className="flex flex-col gap-2 border-t border-border pt-4">
        {canEdit ? (
          <Button asChild>
            <Link href={`${ROUTES.course(item.courseId)}?item=${item.id}`}>
              <LayoutList aria-hidden /> {t('openInBuilder')}
            </Link>
          </Button>
        ) : null}
        <Button asChild variant="secondary">
          <Link href={ROUTES.item(item.courseId, item.id)}>
            <ExternalLink aria-hidden /> {t('open')}
          </Link>
        </Button>
        {canEdit ? (
          <Button
            variant="ghost"
            size="sm"
            className="self-end text-danger hover:bg-danger-soft hover:text-danger"
            onClick={() => {
              deleteItem.mutate({ id: item.id, title: item.title });
              onDeleted();
            }}
          >
            <Trash2 aria-hidden /> {t('delete')}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function MediaInspector({
  itemId,
  moduleTitle,
  canEdit,
  onDeleted,
}: {
  itemId: string | null;
  moduleTitle?: string;
  canEdit: boolean;
  onDeleted: () => void;
}) {
  const t = useTranslations('media');
  const tCommon = useTranslations('common');
  const item = useItem(itemId ?? '');
  if (!itemId)
    return (
      <div className="flex flex-col items-center gap-3 rounded-md border-2 border-dashed border-accent/20 bg-dropzone px-4 py-10 text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-primary-soft text-primary">
          <Images className="size-5" aria-hidden />
        </span>
        <p className="text-sm text-text-muted">{t('selectHint')}</p>
      </div>
    );
  if (item.isLoading) return <SkeletonList label={tCommon('loading')} rows={3} />;
  if (!item.data) return <p className="py-6 text-sm text-danger">{t('loadError')}</p>;
  return (
    <InspectorBody
      item={item.data}
      moduleTitle={moduleTitle}
      canEdit={canEdit}
      onDeleted={onDeleted}
    />
  );
}

function SidebarGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h3 className="px-3.5 pb-1 pt-3 font-sans text-label-sm uppercase text-text-muted">
        {title}
      </h3>
      <ul className="flex flex-col gap-0.5">{children}</ul>
    </div>
  );
}

function SidebarButton({
  active,
  icon: Icon,
  tone,
  label,
  count,
  onClick,
}: {
  active: boolean;
  icon: LucideIcon;
  tone?: BadgeTone;
  label: string;
  count: number;
  onClick: () => void;
}) {
  return (
    <li>
      <button
        type="button"
        aria-pressed={active}
        onClick={onClick}
        className={cn(
          'flex w-full items-center gap-2.5 rounded-full py-1.5 pl-1.5 pr-3 text-left text-sm transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
          active
            ? 'bg-primary font-semibold text-primary-foreground shadow-sm'
            : 'text-text hover:bg-accent/10 hover:text-primary',
        )}
      >
        <span
          className={cn(
            'flex size-7 shrink-0 items-center justify-center rounded-full',
            active ? 'bg-surface/20' : tone ? TONE_CHIP[tone] : 'text-text-muted',
          )}
        >
          <Icon className="size-4" aria-hidden />
        </span>
        <span className="min-w-0 flex-1 truncate">{label}</span>
        <span
          className={cn(
            'min-w-6 rounded-full px-1.5 text-center text-label-md tabular-nums',
            active ? 'bg-surface/20' : 'text-text-muted',
          )}
        >
          {count}
        </span>
      </button>
    </li>
  );
}

/**
 * Медиатека (Stitch «Медиатека и каталог материалов»): header card, stat cards, filter dock (quick access / types /
 * modules), search + upload dropzone, material cards and a file inspector. Everything shown comes from the outline.
 */
export function MediaLibrary() {
  const t = useTranslations('media');
  const tTypes = useTranslations('itemTypes');
  const tCommon = useTranslations('common');
  const tShell = useTranslations('shell');
  const { course, can } = useCourseContext();
  const outline = useOutline(course.id);
  const { createItem } = useOutlineMutations(course.id);
  const videoUpload = useFileUpload('video');
  const contentUpload = useFileUpload('content');
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop, true);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [targetModuleId, setTargetModuleId] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const deferredQuery = useDeferredValue(query.trim().toLowerCase());
  const canEdit = can(PERMISSIONS.courseEdit);

  if (outline.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (outline.isError || !outline.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void outline.refetch()}
      />
    );

  const modules = flattenModules(outline.data.modules);
  const entries: MediaEntry[] = modules.flatMap((module) =>
    module.items
      .filter((item) => (MEDIA_TYPES as readonly string[]).includes(item.type))
      .map((item) => ({ ...item, moduleId: module.id, moduleTitle: module.title })),
  );
  const countOf = (predicate: (entry: MediaEntry) => boolean) => entries.filter(predicate).length;
  const drafts = countOf((entry) => entry.visibility !== 'published');
  const published = entries.length - drafts;
  const visible = entries.filter(
    (entry) =>
      (!filters.type || entry.type === filters.type) &&
      (!filters.moduleId || entry.moduleId === filters.moduleId) &&
      (!filters.draftsOnly || entry.visibility !== 'published') &&
      (!deferredQuery || entry.title.toLowerCase().includes(deferredQuery)),
  );
  const moduleId = targetModuleId || filters.moduleId || modules[0]?.id || '';
  const uploading = videoUpload.isUploading || contentUpload.isUploading;
  const selected = entries.find((entry) => entry.id === selectedId);
  const noFilters = !filters.type && !filters.moduleId && !filters.draftsOnly;
  const activeModule = modules.find((module) => module.id === filters.moduleId);
  const canUpload = canEdit && modules.length > 0;

  const uploadMaterials = async (files: File[]) => {
    if (!moduleId) return;
    for (const file of files) {
      const isVideo = file.type.startsWith('video/');
      const meta = await (isVideo ? videoUpload : contentUpload).upload(file);
      if (!meta) continue;
      const title = file.name.replace(FILE_EXTENSION, '');
      await createItem.mutateAsync({
        moduleId,
        input: isVideo
          ? { type: 'video', title, settings: { kind: 'video', fileId: meta.id, embedUrl: null } }
          : { type: 'file', title, settings: { kind: 'file', fileId: meta.id } },
      });
    }
    toast({ tone: 'success', title: t('uploaded', { count: files.length }) });
  };

  const inspector = (
    <MediaInspector
      itemId={selectedId}
      moduleTitle={selected?.moduleTitle}
      canEdit={canEdit}
      onDeleted={() => setSelectedId(null)}
    />
  );

  const uploadButton = canUpload ? (
    <>
      <input
        ref={fileInput}
        type="file"
        multiple
        accept={UPLOAD_ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          if (files.length > 0) void uploadMaterials(files);
        }}
      />
      <Button
        className="w-full sm:w-auto"
        loading={uploading || createItem.isPending}
        onClick={() => fileInput.current?.click()}
      >
        <UploadCloud aria-hidden /> {t('uploadFiles')}
      </Button>
    </>
  ) : null;

  return (
    <div className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tShell('myCourses'), href: ROUTES.courses },
              { label: course.title, href: ROUTES.course(course.id) },
              { label: t('title') },
            ]}
          />
        }
        title={t('pageTitle')}
        meta={<Badge tone="primary">{t('shownCount', { count: entries.length })}</Badge>}
        description={t('pageDescription')}
        actions={uploadButton}
      />
      <StatGrid>
        <StatCard
          label={t('statTotal')}
          icon={Images}
          value={entries.length}
          unit={t('materialsUnit', { count: entries.length })}
          footer={t('inModules', { count: modules.length })}
        />
        <StatCard
          label={t('statVideo')}
          icon={Video}
          value={countOf((entry) => entry.type === 'video')}
          footer={t('statVideoHint')}
        />
        <StatCard
          label={t('statFiles')}
          icon={FileText}
          tone="warning"
          value={countOf((entry) => entry.type === 'file' || entry.type === 'folder')}
          footer={t('statFilesHint')}
        />
        <StatCard
          label={t('statPublished')}
          icon={FolderOpen}
          tone="success"
          value={entries.length ? `${Math.round((published / entries.length) * PERCENT)}%` : '—'}
          footer={
            entries.length ? (
              <span className="flex flex-col gap-2">
                <span className="h-1.5 overflow-hidden rounded-full bg-surface-container">
                  <span
                    className="block h-full rounded-full bg-success-accent"
                    style={{ width: `${(published / entries.length) * PERCENT}%` }}
                  />
                </span>
                {canEdit ? t('draftsCount', { count: drafts }) : null}
              </span>
            ) : canEdit ? (
              t('draftsCount', { count: drafts })
            ) : undefined
          }
        />
      </StatGrid>
      <div className="flex flex-col gap-gutter xl:flex-row xl:items-start">
        <aside
          aria-label={t('filters')}
          className="min-w-0 xl:sticky xl:top-[calc(var(--size-header)+1rem)] xl:w-64 xl:shrink-0"
        >
          <div className="hidden rounded-lg border border-card-border bg-surface p-3 shadow-sm xl:block">
            <SidebarGroup title={t('quickAccess')}>
              <SidebarButton
                active={noFilters}
                icon={Images}
                tone="primary"
                label={t('all')}
                count={entries.length}
                onClick={() => setFilters(NO_FILTERS)}
              />
              {canEdit ? (
                <SidebarButton
                  active={filters.draftsOnly}
                  icon={PencilLine}
                  tone="neutral"
                  label={t('draftsFilter')}
                  count={drafts}
                  onClick={() => setFilters({ ...NO_FILTERS, draftsOnly: true })}
                />
              ) : null}
            </SidebarGroup>
            <SidebarGroup title={t('typesTitle')}>
              {MEDIA_TYPES.map((type) => (
                <SidebarButton
                  key={type}
                  active={filters.type === type}
                  icon={ITEM_TYPE_ICONS[type]}
                  tone={TYPE_TONE[type]}
                  label={tTypes(type)}
                  count={countOf((entry) => entry.type === type)}
                  onClick={() => setFilters({ ...NO_FILTERS, type })}
                />
              ))}
            </SidebarGroup>
            <SidebarGroup title={t('modulesTitle')}>
              {modules.map((module) => (
                <SidebarButton
                  key={module.id}
                  active={filters.moduleId === module.id}
                  icon={FolderOpen}
                  label={module.title}
                  count={countOf((entry) => entry.moduleId === module.id)}
                  onClick={() => setFilters({ ...NO_FILTERS, moduleId: module.id })}
                />
              ))}
            </SidebarGroup>
          </div>
          {/* Phones/tablets: Stitch chip row filters. */}
          <ul className="scrollbar-none -mx-page-x flex gap-2 overflow-x-auto px-page-x xl:hidden">
            {([null, ...MEDIA_TYPES] as const).map((type) => {
              const active = filters.type === type && !filters.moduleId && !filters.draftsOnly;
              return (
                <li key={type ?? 'all'} className="shrink-0">
                  <button
                    type="button"
                    aria-pressed={active}
                    onClick={() => setFilters({ ...NO_FILTERS, type })}
                    className={cn(
                      'flex h-9 items-center gap-2 whitespace-nowrap rounded-full px-3.5 text-label-lg shadow-sm transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                      active
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-surface text-text-muted hover:bg-accent/10 hover:text-primary',
                    )}
                  >
                    {type ? tTypes(type) : t('all')}
                    <span
                      className={cn(
                        'rounded-full px-1.5 text-label-md',
                        active ? 'bg-surface/20' : 'bg-surface-muted',
                      )}
                    >
                      {type ? countOf((entry) => entry.type === type) : entries.length}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </aside>
        <section className="flex min-w-0 flex-1 flex-col gap-4" aria-label={t('title')}>
          <div className="flex flex-col gap-3 rounded-lg border border-card-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center">
            <div className="relative min-w-0 flex-1">
              <Search
                className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
                aria-hidden
              />
              <Input
                type="search"
                aria-label={t('search')}
                placeholder={t('search')}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="h-10 rounded-full border-transparent bg-surface-muted pl-10"
              />
            </div>
            {canUpload ? (
              <label className="flex min-w-0 items-center gap-2 text-label-md uppercase text-text-muted">
                <span className="shrink-0">{t('targetModule')}</span>
                <NativeSelect
                  className="h-10 min-w-0 flex-1 rounded-full text-sm normal-case sm:w-52 sm:flex-none"
                  value={moduleId}
                  onChange={(event) => setTargetModuleId(event.target.value)}
                >
                  {modules.map((module) => (
                    <option key={module.id} value={module.id}>
                      {module.title}
                    </option>
                  ))}
                </NativeSelect>
              </label>
            ) : null}
          </div>
          {canUpload ? (
            <div className="flex flex-col gap-2">
              <FileDropzone
                layout="inline"
                className="hidden sm:flex"
                accept={UPLOAD_ACCEPT}
                browseLabel={t('browse')}
                hint={t('dropHint')}
                disabled={uploading || createItem.isPending}
                onFiles={(files) => void uploadMaterials(files)}
              />
              {[...videoUpload.uploads, ...contentUpload.uploads].map((entry) => (
                <p
                  key={entry.name}
                  className="flex flex-col gap-1.5 rounded-md bg-surface px-4 py-2.5 text-sm text-text-muted shadow-sm"
                  aria-live="polite"
                >
                  {t('uploading', {
                    name: entry.name,
                    percent: Math.round(entry.progress * PERCENT),
                  })}
                  <span className="h-1.5 overflow-hidden rounded-full bg-surface-container">
                    <span
                      className="block h-full rounded-full bg-primary transition-[width] duration-base"
                      style={{ width: `${Math.round(entry.progress * PERCENT)}%` }}
                    />
                  </span>
                </p>
              ))}
            </div>
          ) : null}
          <p className="flex flex-wrap items-center gap-2 px-1 text-xs text-text-muted">
            <span className="font-semibold text-text">
              {activeModule?.title ??
                (filters.type
                  ? tTypes(filters.type)
                  : filters.draftsOnly
                    ? t('draftsFilter')
                    : t('all'))}
            </span>
            <Badge tone="primary">{t('shownCount', { count: visible.length })}</Badge>
            {!noFilters ? (
              <button
                type="button"
                className="ml-auto rounded-sm font-semibold text-primary hover:underline"
                onClick={() => setFilters(NO_FILTERS)}
              >
                {t('resetFilters')}
              </button>
            ) : null}
          </p>
          {visible.length === 0 ? (
            <EmptyState
              icon={Images}
              title={t('emptyTitle')}
              description={canEdit ? t('emptyTeacher') : t('emptyStudent')}
            />
          ) : (
            <ul className="grid grid-cols-2 gap-3 sm:gap-4 2xl:grid-cols-3">
              {visible.map((entry) => (
                <li key={entry.id}>
                  <MediaCard
                    entry={entry}
                    active={selectedId === entry.id}
                    onSelect={() => setSelectedId(entry.id)}
                  />
                </li>
              ))}
            </ul>
          )}
        </section>
        {isDesktop ? (
          <aside
            aria-label={t('inspector')}
            className="sticky top-[calc(var(--size-header)+1rem)] w-inspector shrink-0"
          >
            <div className="max-h-[calc(100dvh-var(--size-header)-2rem)] overflow-y-auto rounded-lg border border-card-border bg-surface p-5 shadow-sm">
              <h2 className="mb-4 text-xl">{t('inspector')}</h2>
              {inspector}
            </div>
          </aside>
        ) : (
          <Sheet open={!!selectedId} onOpenChange={(open) => !open && setSelectedId(null)}>
            <SheetContent title={t('inspector')} closeLabel={tCommon('close')}>
              {inspector}
            </SheetContent>
          </Sheet>
        )}
      </div>
    </div>
  );
}
