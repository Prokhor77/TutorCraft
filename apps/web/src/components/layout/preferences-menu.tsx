'use client';
import { Monitor, Moon, Sun } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { LOCALES } from '@/i18n/config';
import { cn } from '@/lib/utils/cn';
import { persistLocale } from '@/features/app/locale';
import { THEMES, useUiStore, type ThemePreference } from '@/stores/ui-store';

const themeIcons: Record<ThemePreference, typeof Sun> = { system: Monitor, light: Sun, dark: Moon };

export function ThemeMenu() {
  const t = useTranslations('preferences');
  const theme = useUiStore((state) => state.theme);
  const setTheme = useUiStore((state) => state.setTheme);
  const Icon = themeIcons[theme];
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={t('theme')}>
          <Icon aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>{t('theme')}</DropdownMenuLabel>
        {THEMES.map((option) => {
          const OptionIcon = themeIcons[option];
          return (
            <DropdownMenuItem
              key={option}
              onSelect={() => setTheme(option)}
              aria-checked={theme === option}
              role="menuitemradio"
            >
              <OptionIcon aria-hidden /> {t(`themes.${option}`)}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Switches UI language (NFR-I18N-01). `onChange` lets authenticated users persist it on the profile. */
export function LanguageMenu({ onChange }: { onChange?: (locale: 'ru' | 'en') => void }) {
  const t = useTranslations('preferences');
  const locale = useLocale();
  const router = useRouter();
  const select = (next: 'ru' | 'en') => {
    persistLocale(next);
    onChange?.(next);
    router.refresh();
  };
  return (
    <div
      role="radiogroup"
      aria-label={t('language')}
      className="inline-flex h-8 items-center rounded-full bg-accent/10 p-0.5"
    >
      {LOCALES.map((option) => {
        const active = locale === option;
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={t(`languages.${option}`)}
            title={t(`languages.${option}`)}
            onClick={() => !active && select(option)}
            className={cn(
              'h-7 min-w-9 rounded-full px-2 text-label-md font-semibold uppercase transition-[background-color,color] duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25',
              active
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-text-muted hover:text-primary',
            )}
          >
            {option}
          </button>
        );
      })}
    </div>
  );
}
