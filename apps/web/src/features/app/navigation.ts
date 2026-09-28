import {
  BookOpen,
  CalendarDays,
  ClipboardCheck,
  GraduationCap,
  Home,
  Settings2,
  type LucideIcon,
} from 'lucide-react';
import { ROUTES } from '../auth/routes';

export type NavItem = {
  href: string;
  labelKey: string;
  icon: LucideIcon;
  mobile: boolean;
  counter?: string;
};

export function buildNavigation(hints: {
  teaches: boolean;
  learns: boolean;
  isAdmin: boolean;
}): NavItem[] {
  const items: NavItem[] = [
    { href: ROUTES.home, labelKey: 'home', icon: Home, mobile: true },
    { href: ROUTES.courses, labelKey: 'courses', icon: BookOpen, mobile: true },
  ];
  if (hints.teaches)
    items.push({
      href: ROUTES.grading,
      labelKey: 'grading',
      icon: ClipboardCheck,
      mobile: true,
      counter: 'grading_queue',
    });
  if (hints.learns || !hints.teaches)
    items.push({
      href: ROUTES.grades,
      labelKey: 'grades',
      icon: GraduationCap,
      mobile: !hints.teaches,
    });
  items.push({ href: ROUTES.calendar, labelKey: 'calendar', icon: CalendarDays, mobile: true });
  if (hints.isAdmin)
    items.push({ href: ROUTES.admin, labelKey: 'admin', icon: Settings2, mobile: false });
  return items;
}

export function isActivePath(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}
