import Link from 'next/link';
import { getLocale, getTranslations } from 'next-intl/server';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';
import { LEGAL_DOCUMENTS_VERSION, ORGANIZATION as ORG } from '@/content/legal/organization';
import type { LegalBlock, LegalDocumentContent } from '@/content/legal/types';
import { ROUTES } from '@/features/auth/routes';

function Contacts() {
  const rows: [string, React.ReactNode][] = [
    ['Наименование', ORG.legalName],
    ['УНП', ORG.taxId],
    ['Юридический адрес', ORG.legalAddress],
    ['Директор', ORG.director],
    [
      'Электронная почта',
      <a key="email" href={`mailto:${ORG.email}`} className="text-primary hover:underline">
        {ORG.email}
      </a>,
    ],
    [
      'Телефон',
      <a key="phone" href={`tel:${ORG.phoneHref}`} className="text-primary hover:underline">
        {ORG.phone}
      </a>,
    ],
  ];
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 rounded-lg border border-card-border bg-surface-muted p-5 sm:grid-cols-[12rem_minmax(0,1fr)]">
      {rows.map(([term, value]) => (
        <div key={term} className="contents">
          <dt className="text-sm text-text-muted">{term}</dt>
          <dd className="text-sm">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Block({ block }: { block: LegalBlock }) {
  switch (block.type) {
    case 'text':
      return <p>{block.value}</p>;
    case 'list':
      return (
        <ul className="flex list-disc flex-col gap-1.5 pl-6 marker:text-text-muted">
          {block.items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      );
    case 'table':
      return (
        <div className="overflow-x-auto rounded-lg border border-card-border">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-muted">
              <tr>
                {block.columns.map((column) => (
                  <th key={column} scope="col" className="px-4 py-2.5 font-semibold">
                    {column}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row) => (
                <tr key={row[0]} className="border-t border-card-border align-top">
                  {row.map((cell, index) => (
                    <td
                      key={index}
                      className={index === 0 ? 'px-4 py-2.5 font-medium' : 'px-4 py-2.5'}
                    >
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case 'contacts':
      return <Contacts />;
  }
}

/**
 * Public legal document page (оферта, политика ПД): site header/footer, table of contents and numbered sections.
 * The text itself is Russian only (marked `lang="ru"`) — it is the legally binding version for every UI locale.
 */
export async function LegalDocument({ content }: { content: LegalDocumentContent }) {
  const t = await getTranslations('legal');
  const edition = new Intl.DateTimeFormat(await getLocale(), { dateStyle: 'long' }).format(
    new Date(`${LEGAL_DOCUMENTS_VERSION}T00:00:00Z`),
  );
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <SiteHeader />
      <main id={MAIN_CONTENT_ID} className="flex-1">
        <article className="mx-auto flex max-w-prose flex-col gap-8 px-4 py-10 sm:py-16">
          <header className="flex flex-col gap-3">
            <h1 lang="ru" className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">
              {content.title}
            </h1>
            <p lang="ru" className="text-base text-text-muted">
              {content.lead}
            </p>
            <p className="text-sm text-text-muted">{t('edition', { date: edition })}</p>
            <p className="text-sm text-text-muted">{t('bindingNote')}</p>
          </header>
          <nav
            aria-label={t('toc')}
            className="rounded-lg border border-card-border bg-surface p-5 shadow-sm"
          >
            <h2 className="mb-3 text-label-md uppercase text-text-muted">{t('toc')}</h2>
            <ol lang="ru" className="flex flex-col gap-1.5 text-sm">
              {content.sections.map((section) => (
                <li key={section.id}>
                  <a href={`#${section.id}`} className="rounded-sm hover:text-primary">
                    {section.title}
                  </a>
                </li>
              ))}
            </ol>
          </nav>
          {content.sections.map((section) => (
            <section
              key={section.id}
              id={section.id}
              lang="ru"
              aria-labelledby={`${section.id}-title`}
              className="flex scroll-mt-24 flex-col gap-3 text-base leading-relaxed"
            >
              <h2 id={`${section.id}-title`} className="font-heading text-xl font-semibold">
                {section.title}
              </h2>
              {section.blocks.map((block, index) => (
                <Block key={index} block={block} />
              ))}
            </section>
          ))}
          <footer className="flex flex-wrap gap-x-6 gap-y-2 border-t border-border pt-6 text-sm">
            <Link href={ROUTES.offer} className="rounded-sm text-primary hover:underline">
              {t('offer')}
            </Link>
            <Link href={ROUTES.privacy} className="rounded-sm text-primary hover:underline">
              {t('privacy')}
            </Link>
          </footer>
        </article>
      </main>
      <SiteFooter />
    </div>
  );
}
