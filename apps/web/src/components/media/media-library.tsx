'use client';
import {
  ExternalLink,
  FileText,
  FolderOpen,
  Images,
  LayoutList,
  PencilLine,
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
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Switch } from '@/components/ui/checkbox';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Input, NativeSelect } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
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
  const Icon = ITEM_TYPE_ICONS[entry.type];
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={active}
      className={cn(
        'lift flex h-full w-full flex-col overflow-hidden rounded-md border bg-surface text-left shadow-sm transition-[box-shadow,border-color] duration-fast hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
        active ? 'border-accent shadow-md ring-2 ring-accent/30' : 'border-card-border',
      )}
    >
      <span className="relative flex aspect-video w-full items-center justify-center bg-gradient-to-br from-surface-container to-primary-soft text-primary">
        <Icon className="size-10" aria-hidden />
        <span className="absolute bottom-2 left-2 rounded-full bg-surface/90 px-2 py-0.5 text-label-sm uppercase text-text shadow-sm">
          {tTypes(entry.type)}
        </span>
      </span>
      <span className="flex flex-1 flex-col gap-2 p-4">
        <StatusChip visibility={entry.visibility} className="self-start" />
        <span className="line-clamp-2 font-heading text-base font-semibold">{entry.title}</span>
        <span className="mt-auto flex items-center gap-1.5 truncate text-xs text-primary">
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
  const { patch } = useItemPatcher(item);
  const { deleteItem } = useOutlineMutations(item.courseId);
  const published = item.visibility === 'published';
  return (
    <div className="flex flex-col gap-4">
      <ResourceView item={item} />
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
            className="text-danger hover:bg-danger-soft hover:text-danger"
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
    return <p className="py-10 text-center text-sm text-text-muted">{t('selectHint')}</p>;
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
      <h3 className="px-3.5 pb-1 pt-2 font-sans text-label-sm uppercase text-text-muted">
        {title}
      </h3>
      <ul className="flex flex-col gap-0.5">{children}</ul>
    </div>
  );
}

function SidebarButton({
  active,
  icon: Icon,
  label,
  count,
  onClick,
}: {
  active: boolean;
  icon: LucideIcon;
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
          'flex w-full items-center gap-2.5 rounded-full px-3.5 py-2 text-left text-sm transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
          active
            ? 'bg-primary font-semibold text-primary-foreground'
            : 'text-text hover:bg-accent/10 hover:text-primary',
        )}
      >
        <Icon className="size-4 shrink-0" aria-hidden />
        <span className="min-w-0 flex-1 truncate">{label}</span>
        <span
          className={cn(
            'rounded-full px-2 text-label-md tabular-nums',
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
 * Медиатека (Stitch «Медиатека и каталог материалов»): stat cards, quick access / type / module filters, search,
 * upload strip, material cards and a file inspector. Everything shown comes from the course outline.
 */
export function MediaLibrary() {
  const t = useTranslations('media');
  const tTypes = useTranslations('itemTypes');
  const tCommon = useTranslations('common');
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

  return (
    <div className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        eyebrow={course.title}
        title={t('pageTitle')}
        description={t('pageDescription')}
        actions={
          canEdit && modules.length > 0 ? (
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
                loading={uploading || createItem.isPending}
                onClick={() => fileInput.current?.click()}
              >
                <UploadCloud aria-hidden /> {t('uploadFiles')}
              </Button>
            </>
          ) : null
        }
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
          tone="danger"
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
          footer={canEdit ? t('draftsCount', { count: drafts }) : undefined}
        />
      </StatGrid>
      <div className="flex flex-col gap-gutter xl:flex-row xl:items-start">
        <aside
          aria-label={t('filters')}
          className="xl:sticky xl:top-[calc(var(--size-header)+1rem)] xl:w-64 xl:shrink-0"
        >
          <Card className="flex gap-1 overflow-x-auto p-2 xl:flex-col xl:gap-2 xl:overflow-visible xl:p-3">
            <div className="hidden xl:block">
              <SidebarGroup title={t('quickAccess')}>
                <SidebarButton
                  active={noFilters}
                  icon={Images}
                  label={t('all')}
                  count={entries.length}
                  onClick={() => setFilters(NO_FILTERS)}
                />
                {canEdit ? (
                  <SidebarButton
                    active={filters.draftsOnly}
                    icon={PencilLine}
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
            {/* Phones/tablets: Stitch filter pills. */}
            <ul className="flex gap-1.5 xl:hidden">
              {([null, ...MEDIA_TYPES] as const).map((type) => (
                <li key={type ?? 'all'} className="shrink-0">
                  <button
                    type="button"
                    aria-pressed={filters.type === type && !filters.moduleId}
                    onClick={() => setFilters({ ...NO_FILTERS, type })}
                    className={cn(
                      'flex h-9 items-center gap-2 whitespace-nowrap rounded-full px-3.5 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                      filters.type === type && !filters.moduleId && !filters.draftsOnly
                        ? 'bg-primary text-primary-foreground'
                        : 'text-text-muted hover:bg-accent/10 hover:text-primary',
                    )}
                  >
                    {type ? tTypes(type) : t('all')}
                    <span className="rounded-full bg-surface/20 px-1.5 text-label-md">
                      {type ? countOf((entry) => entry.type === type) : entries.length}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        </aside>
        <section className="flex min-w-0 flex-1 flex-col gap-4" aria-label={t('title')}>
          <div className="relative">
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
              className="pl-10"
            />
          </div>
          {canEdit && modules.length > 0 ? (
            <div className="flex flex-col gap-2">
              <FileDropzone
                layout="inline"
                accept={UPLOAD_ACCEPT}
                browseLabel={t('browse')}
                hint={t('dropHint')}
                disabled={uploading || createItem.isPending}
                onFiles={(files) => void uploadMaterials(files)}
              />
              <label className="flex flex-wrap items-center gap-2 text-sm text-text-muted">
                {t('targetModule')}
                <NativeSelect
                  className="h-9 w-auto min-w-48"
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
              {[...videoUpload.uploads, ...contentUpload.uploads].map((entry) => (
                <p key={entry.name} className="text-sm text-text-muted" aria-live="polite">
                  {t('uploading', {
                    name: entry.name,
                    percent: Math.round(entry.progress * PERCENT),
                  })}
                </p>
              ))}
            </div>
          ) : null}
          <p className="flex flex-wrap items-center gap-2 text-xs text-text-muted">
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
                className="rounded-sm font-semibold text-primary hover:underline"
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
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 2xl:grid-cols-3">
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
            <Card className="max-h-[calc(100dvh-var(--size-header)-2rem)] overflow-y-auto p-5">
              <h2 className="mb-4 text-xl">{t('inspector')}</h2>
              {inspector}
            </Card>
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
