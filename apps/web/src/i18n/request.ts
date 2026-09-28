import { cookies, headers } from 'next/headers';
import { getRequestConfig } from 'next-intl/server';
import {
  DEFAULT_LOCALE,
  isAppLocale,
  LOCALE_COOKIE,
  localeFromAcceptLanguage,
  type AppLocale,
} from './config';

async function resolveLocale(): Promise<AppLocale> {
  const cookieLocale = (await cookies()).get(LOCALE_COOKIE)?.value;
  if (isAppLocale(cookieLocale)) return cookieLocale;
  return localeFromAcceptLanguage((await headers()).get('accept-language')) ?? DEFAULT_LOCALE;
}

export default getRequestConfig(async () => {
  const locale = await resolveLocale();
  const messages = (await import(`../../messages/${locale}.json`)).default;
  return { locale, messages, timeZone: 'UTC' };
});
