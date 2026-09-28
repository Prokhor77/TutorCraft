import {
  BarChart3,
  BookOpen,
  ClipboardCheck,
  FileQuestion,
  GraduationCap,
  Images,
  LayoutList,
  Timer,
  User,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { can, PERMISSIONS } from '@/lib/access/permissions';
import { ROUTES } from '../auth/routes';

export type CourseTab = { href: string; labelKey: string; icon: LucideIcon; match: RegExp };
export type CourseMobileItem = { href: string; labelKey: string; icon: LucideIcon; match: RegExp };

const COURSE_ID_PATTERN = /^\/courses\/([^/?#]+)/;

/** Course context of the current route (`/courses/[id]/...`), used by the shell header and bottom nav. */
export function courseIdFromPath(pathname: string): string | null {
  return COURSE_ID_PATTERN.exec(pathname)?.[1] ?? null;
}

const itemOrRoot = (id: string) => new RegExp(`^/courses/${id}(/items/.*|/trash)?$`);

/**
 * Pill section tabs inside a course (Stitch desktop header). Authoring tabs require `course.edit`;
 * learners get their own set. Permissions come from Course.permissions (FR-ACL-02).
 */
export function courseTabs(courseId: string, permissions: readonly string[]): CourseTab[] {
  if (!can(permissions, PERMISSIONS.courseEdit)) {
    const tabs: CourseTab[] = [
      {
        href: ROUTES.course(courseId),
        labelKey: 'courseStudent',
        icon: BookOpen,
        match: itemOrRoot(courseId),
      },
      { href: ROUTES.courseMedia(courseId), labelKey: 'media', icon: Images, match: /\/media$/ },
    ];
    if (can(permissions, PERMISSIONS.gradeViewOwn)) {
      tabs.push({
        href: ROUTES.courseMyGrades(courseId),
        labelKey: 'myGrades',
        icon: GraduationCap,
        match: /\/grades$/,
      });
    }
    return tabs;
  }
  const tabs: CourseTab[] = [
    {
      href: ROUTES.course(courseId),
      labelKey: 'builder',
      icon: LayoutList,
      match: itemOrRoot(courseId),
    },
  ];
  if (can(permissions, PERMISSIONS.qbankManage)) {
    tabs.push({
      href: ROUTES.courseQuestionBank(courseId),
      labelKey: 'quizBuilder',
      icon: FileQuestion,
      match: /\/question-bank$/,
    });
  }
  tabs.push({
    href: ROUTES.courseMedia(courseId),
    labelKey: 'media',
    icon: Images,
    match: /\/media$/,
  });
  if (can(permissions, PERMISSIONS.gradeViewAll)) {
    tabs.push({
      href: ROUTES.courseGradebook(courseId),
      labelKey: 'analytics',
      icon: BarChart3,
      match: /\/gradebook$/,
    });
  }
  if (can(permissions, PERMISSIONS.enrollmentView)) {
    tabs.push({
      href: ROUTES.courseParticipants(courseId),
      labelKey: 'participants',
      icon: Users,
      match: /\/participants$/,
    });
  }
  return tabs;
}

/** Mobile bottom navigation inside a course: Курс · Тесты · Прогресс · Медиа · Профиль (Stitch). */
export function courseMobileNav(
  courseId: string,
  permissions: readonly string[],
): CourseMobileItem[] {
  const author = can(permissions, PERMISSIONS.qbankManage);
  const analytics = can(permissions, PERMISSIONS.gradeViewAll);
  return [
    {
      href: ROUTES.course(courseId),
      labelKey: 'mobileCourse',
      icon: BookOpen,
      match: itemOrRoot(courseId),
    },
    {
      href: author ? ROUTES.courseQuestionBank(courseId) : ROUTES.courseTests(courseId),
      labelKey: 'mobileTests',
      icon: Timer,
      match: author ? /\/question-bank$/ : /\?type=quiz$/,
    },
    // Teachers: «Проверка» opens the course grading queue (Stitch grading-mobile); learners: own grades.
    analytics
      ? {
          href: `${ROUTES.courseGradebook(courseId)}?tab=queue`,
          labelKey: 'mobileGrading',
          icon: ClipboardCheck,
          match: /\/gradebook(\?|$)/,
        }
      : {
          href: ROUTES.courseMyGrades(courseId),
          labelKey: 'mobileProgress',
          icon: BarChart3,
          match: /\/grades$/,
        },
    {
      href: ROUTES.courseMedia(courseId),
      labelKey: 'mobileMedia',
      icon: Images,
      match: /\/media$/,
    },
    { href: ROUTES.profile, labelKey: 'mobileProfile', icon: User, match: /^\/settings\/profile$/ },
  ];
}

/**
 * Index of the active course nav entry. Entries whose href carries a query (e.g. «Тесты» → `?type=quiz`) are
 * matched against the full location first, so they win over the path-only entry for the same page.
 */
export function activeCourseNavIndex(
  items: readonly Pick<CourseMobileItem, 'href' | 'match'>[],
  pathname: string,
  search: string,
): number {
  const location = search ? `${pathname}?${search}` : pathname;
  const withQuery = items.findIndex((item) => item.href.includes('?') && item.match.test(location));
  if (withQuery !== -1) return withQuery;
  return items.findIndex((item) => !item.href.includes('?') && item.match.test(pathname));
}
