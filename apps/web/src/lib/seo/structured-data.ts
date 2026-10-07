/**
 * schema.org JSON-LD builders. Pure functions over plain data (the site origin is passed in), so the same graph is
 * easy to unit-test. Nodes reference each other by `@id`; a reference is only emitted when its node is in the
 * same graph — no dangling ids.
 *
 * HONESTY RULE (as in content/landing.ts): only facts that are visible on the page. No invented ratings, review
 * counts or prices — Google treats markup that is not backed by visible content as spam.
 */

export type JsonLdNode = Record<string, unknown>;
export type JsonLdGraph = { '@context': 'https://schema.org'; '@graph': JsonLdNode[] };

export function graph(...nodes: (JsonLdNode | null | undefined)[]): JsonLdGraph {
  return {
    '@context': 'https://schema.org',
    '@graph': nodes.filter((node): node is JsonLdNode => !!node),
  };
}

export const ids = {
  organization: (origin: string) => `${origin}/#organization`,
  website: (origin: string) => `${origin}/#website`,
  product: (origin: string) => `${origin}/#software`,
  webpage: (url: string) => `${url}#webpage`,
  breadcrumb: (url: string) => `${url}#breadcrumb`,
  school: (origin: string, tenantSlug: string) => `${origin}/c/${tenantSlug}#school`,
  course: (url: string) => `${url}#course`,
};

/** BCP 47 language for `inLanguage`: the UI language (course content itself is single-language). */
export function schemaLanguage(locale: string): string {
  return locale.slice(0, 2) || 'ru';
}

export function organizationNode(
  origin: string,
  opts: { name: string; email?: string | null; sameAs?: string[] },
) {
  return {
    '@type': 'Organization',
    '@id': ids.organization(origin),
    name: opts.name,
    url: `${origin}/`,
    logo: { '@type': 'ImageObject', url: `${origin}/icons/icon-512.png`, width: 512, height: 512 },
    ...(opts.email ? { email: opts.email } : {}),
    ...(opts.sameAs?.length ? { sameAs: opts.sameAs } : {}),
  };
}

/** `inLanguage` lists every UI language: the website node is shared by all pages, whatever their language. */
export function websiteNode(
  origin: string,
  opts: { name: string; description: string; languages: string[] },
) {
  return {
    '@type': 'WebSite',
    '@id': ids.website(origin),
    url: `${origin}/`,
    name: opts.name,
    description: opts.description,
    inLanguage: opts.languages,
    publisher: { '@id': ids.organization(origin) },
  };
}

export type WebPageInput = {
  url: string;
  name: string;
  description: string;
  locale: string;
  hasBreadcrumb?: boolean;
  /** `@id` of the node the page is about (product, course, school). */
  about?: string;
  type?: 'WebPage' | 'CollectionPage' | 'ItemPage';
};

export function webPageNode(origin: string, page: WebPageInput) {
  return {
    '@type': page.type ?? 'WebPage',
    '@id': ids.webpage(page.url),
    url: page.url,
    name: page.name,
    description: page.description,
    inLanguage: schemaLanguage(page.locale),
    isPartOf: { '@id': ids.website(origin) },
    ...(page.about ? { about: { '@id': page.about }, mainEntity: { '@id': page.about } } : {}),
    ...(page.hasBreadcrumb ? { breadcrumb: { '@id': ids.breadcrumb(page.url) } } : {}),
  };
}

export type Crumb = { name: string; url: string };

/** Only for two or more crumbs: a one-item trail is noise for search engines. */
export function breadcrumbNode(pageUrl: string, crumbs: Crumb[]) {
  if (crumbs.length < 2) return null;
  return {
    '@type': 'BreadcrumbList',
    '@id': ids.breadcrumb(pageUrl),
    itemListElement: crumbs.map((crumb, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: crumb.name,
      item: crumb.url,
    })),
  };
}

export type PlanOffer = { name: string; price: number; currency: string; months: number };

/** The product itself: a web app sold as a school subscription (ADR-012) — the prices are those on the landing. */
export function softwareNode(
  origin: string,
  opts: {
    name: string;
    description: string;
    locale: string;
    features: string[];
    plans: PlanOffer[];
  },
) {
  return {
    '@type': 'SoftwareApplication',
    '@id': ids.product(origin),
    name: opts.name,
    description: opts.description,
    url: `${origin}/`,
    applicationCategory: 'EducationalApplication',
    applicationSubCategory: 'LMS',
    operatingSystem: 'Web',
    inLanguage: schemaLanguage(opts.locale),
    featureList: opts.features,
    publisher: { '@id': ids.organization(origin) },
    offers: opts.plans.map((plan) => ({
      '@type': 'Offer',
      name: plan.name,
      price: plan.price,
      priceCurrency: plan.currency,
      url: `${origin}/#pricing`,
      availability: 'https://schema.org/InStock',
      priceSpecification: {
        '@type': 'UnitPriceSpecification',
        price: plan.price,
        priceCurrency: plan.currency,
        referenceQuantity: { '@type': 'QuantitativeValue', value: plan.months, unitCode: 'MON' },
      },
    })),
  };
}

export function schoolNode(origin: string, tenantSlug: string, name: string) {
  return {
    '@type': 'EducationalOrganization',
    '@id': ids.school(origin, tenantSlug),
    name,
    url: `${origin}/c/${tenantSlug}`,
  };
}

export type CourseInput = {
  url: string;
  name: string;
  description: string;
  locale: string;
  tenantSlug: string;
  teacherName: string;
  imageUrl: string;
  moduleTitles: string[];
};

/**
 * A storefront course. Courses are free for students (ADR-012), hence the zero-price offer; without `offers` and
 * `hasCourseInstance` Google does not show the course rich result.
 */
export function courseNode(origin: string, course: CourseInput) {
  const instructor = course.teacherName
    ? { '@type': 'Person', name: course.teacherName }
    : undefined;
  return {
    '@type': 'Course',
    '@id': ids.course(course.url),
    url: course.url,
    name: course.name,
    description: course.description || course.name,
    inLanguage: schemaLanguage(course.locale),
    image: course.imageUrl,
    isAccessibleForFree: true,
    provider: { '@id': ids.school(origin, course.tenantSlug) },
    ...(instructor ? { instructor } : {}),
    ...(course.moduleTitles.length
      ? { syllabusSections: course.moduleTitles.map((name) => ({ '@type': 'Syllabus', name })) }
      : {}),
    offers: {
      '@type': 'Offer',
      category: 'Free',
      price: 0,
      priceCurrency: 'USD',
      url: course.url,
      availability: 'https://schema.org/InStock',
    },
    hasCourseInstance: {
      '@type': 'CourseInstance',
      courseMode: 'Online',
      ...(instructor ? { instructor } : {}),
    },
  };
}

export function courseListNode(pageUrl: string, courses: { name: string; url: string }[]) {
  if (courses.length === 0) return null;
  return {
    '@type': 'ItemList',
    '@id': `${pageUrl}#courses`,
    numberOfItems: courses.length,
    itemListElement: courses.map((course, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      url: course.url,
      name: course.name,
    })),
  };
}

/** Serialises a graph for an inline `<script type="application/ld+json">`; `<` is escaped so `</script>` can't break out. */
export function serializeJsonLd(data: JsonLdGraph): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}
