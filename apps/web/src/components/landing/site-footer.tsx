import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Brand } from '@/components/layout/brand';
import { LANDING } from '@/content/landing';
import { ORGANIZATION } from '@/content/legal/organization';
import { ROUTES } from '@/features/auth/routes';
import { LANDING_ANCHORS } from './site-header';

type FooterLink = { href: string; label: string; external?: boolean };

function FooterColumn({ title, links }: { title: string; links: FooterLink[] }) {
  return (
    <div className="flex flex-col gap-3">
      <h2 className="font-sans text-label-md uppercase text-text-muted">{title}</h2>
      <ul className="flex flex-col gap-2">
        {links.map((link) => (
          <li key={link.href}>
            {link.external ? (
              <a
                href={link.href}
                className="rounded-sm text-sm text-text hover:text-primary"
                rel="noopener noreferrer"
              >
                {link.label}
              </a>
            ) : (
              <Link href={link.href} className="rounded-sm text-sm text-text hover:text-primary">
                {link.label}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Public site footer: brand + description and link columns. Only real destinations are listed; the «Компания»
 * column appears when contacts are configured in `src/content/landing.ts`. Legal documents and the
 * operator's requisites are always shown (required for a public offer).
 */
export async function SiteFooter() {
  const t = await getTranslations('landing.footer');
  const tLanding = await getTranslations('landing');
  const { email, telegram } = LANDING.contacts;
  const company: FooterLink[] = [
    ...(email ? [{ href: `mailto:${email}`, label: t('email'), external: true }] : []),
    ...(telegram ? [{ href: telegram, label: t('telegram'), external: true }] : []),
  ];
  return (
    <footer className="border-t border-card-border bg-surface-muted">
      <div className="mx-auto flex max-w-content flex-col gap-10 px-page-x py-12">
        <nav
          aria-label={t('label')}
          className="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(5,minmax(0,1fr))]"
        >
          <div className="flex flex-col gap-3 sm:col-span-2 lg:col-span-1">
            <Brand name={tLanding('product')} />
            <p className="max-w-xs text-sm text-text-muted">{t('description')}</p>
          </div>
          <FooterColumn
            title={t('product')}
            links={[
              { href: `/#${LANDING_ANCHORS.features}`, label: t('features') },
              { href: `/#${LANDING_ANCHORS.calculator}`, label: t('calculator') },
              { href: `/#${LANDING_ANCHORS.pricing}`, label: t('pricing') },
            ]}
          />
          <FooterColumn
            title={t('teachers')}
            links={[
              { href: ROUTES.register, label: t('register') },
              { href: ROUTES.login, label: t('login') },
              { href: ROUTES.forgotPassword, label: t('forgot') },
            ]}
          />
          <FooterColumn
            title={t('students')}
            links={[
              { href: ROUTES.home, label: t('tasks') },
              { href: ROUTES.grades, label: t('grades') },
              { href: ROUTES.calendar, label: t('calendar') },
            ]}
          />
          <FooterColumn
            title={t('documents')}
            links={[
              { href: ROUTES.offer, label: t('offer') },
              { href: ROUTES.privacy, label: t('privacy') },
            ]}
          />
          {company.length > 0 ? <FooterColumn title={t('company')} links={company} /> : null}
        </nav>
        <div className="flex flex-col gap-1 border-t border-border pt-6 text-xs text-text-muted">
          <p>{t('rights', { year: new Date().getFullYear() })}</p>
          <p>
            {t('requisites', {
              name: ORGANIZATION.shortName,
              taxId: ORGANIZATION.taxId,
              address: ORGANIZATION.legalAddress,
            })}
          </p>
        </div>
      </div>
    </footer>
  );
}
