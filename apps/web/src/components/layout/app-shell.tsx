'use client';
import { Check, ChevronDown, ChevronRight, LayoutGrid } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Suspense, type ReactNode } from 'react';
import { SubscriptionBanner } from '@/components/billing/subscription-banner';
import { CountBadge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  activeCourseNavIndex,
  courseIdFromPath,
  courseMobileNav,
  courseTabs,
} from '@/features/app/course-navigation';
import { buildNavigation, isActivePath, type NavItem } from '@/features/app/navigation';
import { ROUTES } from '@/features/auth/routes';
import { useMe, useUpdateMe } from '@/features/auth/use-auth';
import { CourseProvider } from '@/features/courses/course-context';
import { useCourse } from '@/features/courses/use-courses';
import { roleHints, useMyCourses } from '@/features/courses/use-my-courses';
import { useItem } from '@/features/items/use-item';
import { isPlatformAdminHint, isSchoolOwnerHint } from '@/lib/access/permissions';
import type { ItemType } from '@/lib/api/schemas/common';
import type { Course } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { useUiStore } from '@/stores/ui-store';
import { Brand, PRODUCT_NAME } from './brand';
import { CourseActions } from './course-actions';
import { NotificationsBell } from './notifications-bell';
import { LanguageMenu, ThemeMenu } from './preferences-menu';
import { MAIN_CONTENT_ID } from './skip-link';
import { UserMenu } from './user-menu';

const ITEM_ID_PATTERN = /\/items\/([^/?#]+)/;

type PillLink = {
  href: string;
  label: string;
  active: boolean;
  counter?: number;
  icon: NavItem['icon'];
};

/** Pill section tabs (Stitch header). */
function PillTabs({ links, label }: { links: PillLink[]; label: string }) {
  return (
    <nav aria-label={label} className="scrollbar-none min-w-0 overflow-x-auto">
      <ul className="flex items-center gap-1 rounded-full bg-surface-muted p-1">
        {links.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              aria-current={link.active ? 'page' : undefined}
              className={cn(
                'flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                link.active
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'text-text-muted hover:bg-surface hover:text-primary',
              )}
            >
              {link.label}
              {link.counter ? (
                <CountBadge count={link.counter} label={String(link.counter)} />
              ) : null}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function Breadcrumbs({ course, itemTitle }: { course: Course; itemTitle?: string }) {
  const t = useTranslations('shell');
  const crumbs = [
    { href: ROUTES.courses, label: t('myCourses') },
    { href: ROUTES.course(course.id), label: course.title },
    ...(itemTitle ? [{ href: '', label: itemTitle }] : []),
  ];
  return (
    <nav aria-label={t('breadcrumbs')} className="hidden min-w-0 2xl:block">
      <ol className="flex items-center gap-1 text-sm text-text-muted">
        {crumbs.map((crumb, index) => (
          <li key={crumb.label + index} className="flex min-w-0 items-center gap-1">
            {index > 0 ? <ChevronRight className="size-3.5 shrink-0" aria-hidden /> : null}
            {crumb.href && index < crumbs.length - 1 ? (
              <Link href={crumb.href} className="max-w-40 truncate rounded-sm hover:text-primary">
                {crumb.label}
              </Link>
            ) : (
              <span aria-current="page" className="max-w-48 truncate font-medium text-text">
                {crumb.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

function useLocationKey(): string {
  const pathname = usePathname();
  const search = useSearchParams().toString();
  return search ? `${pathname}?${search}` : pathname;
}

function useGlobalNavigation(): NavItem[] {
  const me = useMe();
  const { data: courses } = useMyCourses();
  return buildNavigation({
    ...roleHints(courses),
    isAdmin: isPlatformAdminHint(me?.tenantRoles),
    ownsSchool: isSchoolOwnerHint(me?.tenantRoles),
  });
}

function GlobalTabs() {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const counters = useUiStore((state) => state.counters);
  const links = useGlobalNavigation().map((item) => ({
    href: item.href,
    label: t(item.labelKey),
    icon: item.icon,
    active: isActivePath(pathname, item.href),
    counter: item.counter ? counters[item.counter] : undefined,
  }));
  return <PillTabs links={links} label={t('primary')} />;
}

/** Course sections with the active one resolved (a quiz item page belongs to «Конструктор тестов»). */
function useCourseSectionLinks(course: Course, itemType?: ItemType): PillLink[] {
  const t = useTranslations('shell');
  const pathname = useLocationKey().split('?')[0] ?? '';
  const tabs = courseTabs(course.id, course.permissions);
  const quizOwner =
    itemType === 'quiz' && tabs.some((tab) => tab.labelKey === 'quizBuilder')
      ? 'quizBuilder'
      : null;
  return tabs.map((tab) => ({
    href: tab.href,
    label: t(tab.labelKey),
    icon: tab.icon,
    active: quizOwner ? tab.labelKey === quizOwner : tab.match.test(pathname),
  }));
}

/**
 * Course section switcher: one button with the current section that opens a menu of all sections,
 * instead of a horizontally scrolling tab strip that did not fit next to the course actions.
 */
function CourseSectionMenu({ course, itemType }: { course: Course; itemType?: ItemType }) {
  const t = useTranslations('shell');
  const links = useCourseSectionLinks(course, itemType);
  const current = links.find((link) => link.active);
  const CurrentIcon = current?.icon ?? LayoutGrid;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={current ? `${t('courseSections')}: ${current.label}` : t('courseSections')}
        className="group flex h-10 min-w-0 max-w-full items-center gap-2 rounded-full bg-surface-muted py-1 pl-1 pr-3 text-label-lg transition-colors duration-fast hover:bg-surface-container focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 data-[state=open]:bg-surface-container"
      >
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm">
          <CurrentIcon className="size-4" aria-hidden />
        </span>
        <span className="truncate">{current?.label ?? t('courseSections')}</span>
        <ChevronDown
          className="size-4 shrink-0 text-text-muted transition-transform duration-fast group-data-[state=open]:rotate-180"
          aria-hidden
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="center" className="min-w-60">
        <DropdownMenuLabel>{t('courseSections')}</DropdownMenuLabel>
        {links.map((link) => (
          <DropdownMenuItem
            key={link.href}
            asChild
            className={cn(link.active && 'bg-accent/10 font-semibold text-primary')}
          >
            <Link href={link.href} aria-current={link.active ? 'page' : undefined}>
              <link.icon aria-hidden className={cn(link.active && '!text-primary')} />
              <span className="flex-1">{link.label}</span>
              {link.active ? <Check aria-hidden className="!text-primary" /> : null}
            </Link>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Label of the current section for the Stitch mobile header («TUTORCRAFT / Конструктор курса»). */
function useSectionLabel(course: Course | undefined): string | undefined {
  const tShell = useTranslations('shell');
  const tNav = useTranslations('nav');
  const pathname = usePathname();
  const global = useGlobalNavigation();
  if (course) {
    const tab = courseTabs(course.id, course.permissions).find((entry) =>
      entry.match.test(pathname),
    );
    return tab ? tShell(tab.labelKey) : course.title;
  }
  const item = global.find((entry) => isActivePath(pathname, entry.href));
  return item ? tNav(item.labelKey) : undefined;
}

function MobileBrand({ course, tenantName }: { course: Course | undefined; tenantName?: string }) {
  const section = useSectionLabel(course);
  return (
    <Brand
      href={ROUTES.home}
      eyebrow={section ? (tenantName ?? PRODUCT_NAME) : undefined}
      name={section ?? tenantName}
      className="min-w-0 flex-1 md:hidden"
    />
  );
}

function BottomNavLink({ href, label, icon: Icon, active, counter }: PillLink) {
  return (
    <Link
      href={href}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'relative flex flex-1 flex-col items-center justify-center gap-1 rounded-full text-[11px] font-semibold transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
        active ? 'text-primary' : 'text-text-muted hover:text-text',
      )}
    >
      <span
        className={cn(
          'flex h-7 w-12 items-center justify-center rounded-full transition-colors duration-fast',
          active && 'bg-accent/15',
        )}
      >
        <Icon className="size-5" aria-hidden />
      </span>
      <span className="truncate">{label}</span>
      {counter ? (
        <CountBadge count={counter} label={String(counter)} className="absolute right-1/4 top-0" />
      ) : null}
    </Link>
  );
}

function MobileNav({ course }: { course: Course | undefined }) {
  const tNav = useTranslations('nav');
  const tShell = useTranslations('shell');
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const counters = useUiStore((state) => state.counters);
  const globalItems = useGlobalNavigation();
  const courseItems = course ? courseMobileNav(course.id, course.permissions) : [];
  const activeIndex = activeCourseNavIndex(courseItems, pathname, search);
  const links: PillLink[] = course
    ? courseItems.map((item, index) => ({
        href: item.href,
        label: tShell(item.labelKey),
        icon: item.icon,
        active: index === activeIndex,
      }))
    : globalItems
        .filter((item) => item.mobile)
        .map((item) => ({
          href: item.href,
          label: tNav(item.labelKey),
          icon: item.icon,
          active: isActivePath(pathname, item.href),
          counter: item.counter ? counters[item.counter] : undefined,
        }));
  return (
    <nav
      aria-label={course ? tShell('courseSections') : tNav('primary')}
      className="glass fixed inset-x-0 bottom-0 z-30 flex h-bottom-nav items-stretch gap-1 border-t border-border px-2 pb-[env(safe-area-inset-bottom)] pt-1 md:hidden"
    >
      {links.map((link) => (
        <BottomNavLink key={link.href} {...link} />
      ))}
    </nav>
  );
}

/**
 * App shell (Stitch): glass top header with logo, breadcrumbs, pill tabs for global sections outside a
 * course and a section switcher menu inside one — plus «Предпросмотр» / «Опубликовать курс»; bottom navigation on mobile.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const me = useMe();
  const updateMe = useUpdateMe();
  const courseId = courseIdFromPath(pathname);
  const course = useCourse(courseId ?? '', !!courseId).data;
  const itemId = ITEM_ID_PATTERN.exec(pathname)?.[1] ?? '';
  const itemData = useItem(itemId).data;
  const itemTitle = itemData?.title;
  const tenantName = me?.tenant.name;

  const header = (
    <header className="glass sticky top-0 z-30 border-b border-border">
      <div className="mx-auto flex h-header max-w-content items-center gap-3 px-page-x">
        <Suspense>
          <MobileBrand course={course} tenantName={tenantName} />
        </Suspense>
        <Brand
          href={ROUTES.home}
          name={tenantName}
          logoUrl={me?.tenant.branding.logoUrl}
          className={cn('hidden shrink-0 md:flex', course ? 'max-w-48 2xl:max-w-56' : 'max-w-56')}
        />
        {course ? <Breadcrumbs course={course} itemTitle={itemTitle} /> : null}
        <div className="hidden min-w-0 flex-1 justify-center md:flex">
          <Suspense>
            {course ? (
              <CourseSectionMenu course={course} itemType={itemData?.type} />
            ) : (
              <GlobalTabs />
            )}
          </Suspense>
        </div>
        <div className="ml-auto flex shrink-0 items-center gap-1">
          {course ? <CourseActions /> : null}
          <span className="hidden 2xl:contents">
            <LanguageMenu onChange={(locale) => updateMe.mutate({ locale })} />
            <ThemeMenu />
          </span>
          <NotificationsBell />
          <UserMenu />
        </div>
      </div>
    </header>
  );

  return (
    <div className="min-h-dvh bg-background">
      {course ? <CourseProvider course={course}>{header}</CourseProvider> : header}
      <main
        id={MAIN_CONTENT_ID}
        tabIndex={-1}
        className="pb-safe-nav mx-auto w-full max-w-content px-page-x py-page-y focus:outline-none md:pb-page-y"
      >
        <SubscriptionBanner />
        {children}
      </main>
      <Suspense>
        <MobileNav course={course} />
      </Suspense>
    </div>
  );
}
