# SEO

Search engines and link previews see only the public, server-rendered part of the site: the landing `/`, school
storefronts `/c/[tenantSlug]` and course landings `/c/[tenantSlug]/[courseSlug]`. Everything else is `noindex`.
All SEO decisions live in `src/lib/seo/`. Pages call it and never build meta tags by hand.

## Module

| File                             | What it does                                                                                                        |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `src/lib/seo/site.ts`            | `siteUrl()` (canonical origin), `absoluteUrl()`, `indexingAllowed()`. Read at request time, like `CORE_API_URL`.    |
| `src/lib/seo/metadata.ts`        | robots presets, `pageMetadata()` (title, description, canonical, Open Graph, Twitter in one call), `excerpt()`.     |
| `src/lib/seo/structured-data.ts` | schema.org JSON-LD builders (pure functions), `serializeJsonLd()` (escapes `<`).                                    |
| `src/lib/seo/og-image.tsx`       | 1200×630 link-preview card for the `opengraph-image` routes (Satori, fonts from `src/assets/og/`).                  |
| `src/components/seo/json-ld.tsx` | `<JsonLd data={graph(...)} />`: server-rendered `<script type="application/ld+json">`.                              |
| `src/app/robots.txt/route.ts`    | robots.txt. A Route Handler, because `app/robots.ts` cannot emit Yandex `Clean-param`.                              |
| `src/app/sitemap.ts`             | sitemap.xml: the landing plus every storefront and course from core-api `GET /public/sitemap`, with real `lastmod`. |
| `src/lib/seo/seo.test.ts`        | Title ≤ 60 and description ≤ 160 characters for the static copy, robots presets, JSON-LD invariants.                |

## Robots presets

| Preset     | Meta robots                                 | Used for                                                                             |
| ---------- | ------------------------------------------- | ------------------------------------------------------------------------------------ |
| `public`   | `index, follow, max-image-preview:large, …` | landing, storefront with courses, course landing                                     |
| `unlisted` | `noindex, follow` (**root layout default**) | legal pages, auth forms, empty storefront, anything that did not opt in              |
| `private`  | `noindex, nofollow`                         | `(app)` (the authenticated app renders a spinner for crawlers), `/join/[token]`, 404 |

A page is indexable only if it passes `robots: 'public'` (the `pageMetadata` default). A new page with no metadata
inherits `unlisted`, so forgetting the metadata keeps it out of the index rather than letting a thin page in.

robots.txt disallows only `/api/`. App, auth and invite pages are **not** disallowed: a crawler that robots.txt
blocks never fetches the page, so it never sees the `noindex` and can still index the bare URL from a link (the
footer links to `/home`, `/grades`, …). User files get `X-Robots-Tag: noindex, nofollow, noimageindex`
(`next.config.ts`, plus `location /storage/` in nginx, which bypasses Next.js).

## Environment

| Variable                   | Meaning                                                                                                        |
| -------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `SITE_URL`                 | Canonical origin, e.g. `https://tutorcraft.ru`. Falls back to `PUBLIC_BASE_URL`, then `http://localhost:3000`. |
| `SEO_INDEXING`             | `true` / `false`. **Unset means auto:** indexing is allowed only when `SITE_URL` is a real domain.             |
| `YANDEX_VERIFICATION`      | `content` value of the Yandex.Webmaster `<meta name="yandex-verification">` tag.                               |
| `GOOGLE_SITE_VERIFICATION` | `content` value of the Google Search Console `<meta name="google-site-verification">` tag.                     |

While production runs on a bare IP, indexing is off automatically: robots.txt is `Disallow: /`, every page is
`noindex, nofollow` and the sitemap is empty. An indexed IP would compete with the future domain as a duplicate.
`infra/k8s/app/web.yaml` passes `PUBLIC_BASE_URL` to the web container as `SITE_URL`.

### Going live on a domain

1. Set `PUBLIC_BASE_URL=https://<domain>` (and the TLS settings from `docs/deployment.md`). Indexing turns on by itself.
2. Make the IP and `www` 301-redirect to the canonical host: `infra/deploy/nginx/tutorcraft.conf` already does this
   for `www` and plain HTTP. One canonical host and scheme is a prerequisite for canonical URLs to mean anything.
3. Add the site to Yandex.Webmaster and Google Search Console and put the codes into `YANDEX_VERIFICATION` /
   `GOOGLE_SITE_VERIFICATION`. Submit `https://<domain>/sitemap.xml` in both.
4. In Yandex.Webmaster set the region (Беларусь / Россия), which Yandex does not take from markup.
5. Check with the Rich Results Test (Course, BreadcrumbList) and the Yandex microdata validator.

## Structured data

Every public page renders one JSON-LD graph. Nodes are linked by `@id`, and a node is referenced only if it is in
the same graph.

| Page              | Nodes                                                                                                                           |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `/`               | `Organization`, `WebSite`, `WebPage`, `SoftwareApplication` with one `Offer` per plan from `src/content/landing.ts`             |
| `/c/[tenant]`     | `EducationalOrganization` (the school), `CollectionPage`, `BreadcrumbList`, `ItemList` of courses                               |
| `/c/[tenant]/[c]` | `EducationalOrganization`, `Course` (free `Offer`, `CourseInstance` online, instructor, syllabus), `ItemPage`, `BreadcrumbList` |

The honesty rule from `src/content/landing.ts` applies here too: only facts that are visible on the page. Do not add
`AggregateRating` or `Review` until real reviews are shown. Do not add `FAQPage` without a visible FAQ. Google treats
markup that is not backed by content as spam.

## Open Graph images

`opengraph-image.tsx` routes render a branded 1200×630 PNG:

- `/` shows the product tagline;
- the storefront shows the school name and course count;
- the course page shows the title, school and teacher.

Course covers are not used, for two reasons. They are pre-signed storage URLs that expire after `download-url-ttl`
(10 minutes). Crawlers and messengers cache previews for days.

Satori (`next/og`) needs static TTF/OTF fonts, not WOFF2 or variable fonts. `src/assets/og/Onest-{500,700}.ttf` are
static Latin + Cyrillic instances of the self-hosted Onest (SIL OFL 1.1). They were built with fontTools
(`varLib.instancer` at `wght` 500/700, then `fontTools.merge` of the latin and cyrillic subsets) from
`@fontsource-variable/onest`. `outputFileTracingIncludes` in `next.config.ts` copies them into the standalone build.

## Locales and hreflang

The UI language comes from a cookie or `Accept-Language`, and every URL serves every locale. Crawlers send neither,
so they always see Russian (`DEFAULT_LOCALE`). Without per-locale URLs there is nothing to point `hreflang` at, so the
site emits no `hreflang`, sets `og:locale` from the request locale and keeps the Russian page as the canonical one.
Indexing EN/UZ separately would need locale-prefixed routes (`/en/...`), and then `alternates.languages` in
`pageMetadata` and in the sitemap.

## Checklist for a new public page

1. `generateMetadata` returns `pageMetadata({ path, title, description, locale })`. Keep the title ≤ 60 characters and
   the description ≤ 160, and add both to `seo.test.ts` if they are static copy.
2. Render `<JsonLd data={graph(...)} />` with a `WebPage` node, plus `breadcrumbNode` when the page is nested.
3. Add the URL to `src/app/sitemap.ts`, and only if the page is `public`.
4. Link it from an indexable page: a URL nothing links to is found late or never.
