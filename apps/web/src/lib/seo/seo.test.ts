import { afterEach, describe, expect, it, vi } from 'vitest';
import en from '../../../messages/en.json';
import ru from '../../../messages/ru.json';
import { excerpt, pageMetadata, robots } from './metadata';
import { absoluteUrl, indexingAllowed, normalizePath, siteUrl } from './site';
import { breadcrumbNode, courseNode, graph, serializeJsonLd, webPageNode } from './structured-data';

/** Search engines cut titles at ~60 characters and descriptions at ~160 (counted in code points). */
const TITLE_MAX = 60;
const DESCRIPTION_MIN = 70;
const DESCRIPTION_MAX = 160;
const length = (text: string) => [...text].length;

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('static SEO copy', () => {
  it.each([
    ['ru', ru],
    ['en', en],
  ])('%s titles and descriptions fit search snippets', (_, messages) => {
    for (const title of [messages.meta.title, messages.seo.landingTitle]) {
      expect(length(title), title).toBeLessThanOrEqual(TITLE_MAX);
    }
    for (const description of [messages.meta.description, messages.seo.landingDescription]) {
      expect(length(description), description).toBeLessThanOrEqual(DESCRIPTION_MAX);
    }
    expect(length(messages.seo.landingDescription)).toBeGreaterThanOrEqual(DESCRIPTION_MIN);
  });
});

describe('site', () => {
  it('normalises the origin and paths', () => {
    vi.stubEnv('SITE_URL', 'https://tutorcraft.example/');
    expect(siteUrl()).toBe('https://tutorcraft.example');
    expect(absoluteUrl('c/demo/')).toBe('https://tutorcraft.example/c/demo');
    expect(normalizePath('/')).toBe('/');
  });

  it('keeps a bare IP or localhost out of the index unless forced', () => {
    vi.stubEnv('SITE_URL', 'http://91.149.179.186');
    expect(indexingAllowed()).toBe(false);
    vi.stubEnv('SEO_INDEXING', 'true');
    expect(indexingAllowed()).toBe(true);
  });

  it('indexes a real domain by default and honours an explicit opt-out', () => {
    vi.stubEnv('SITE_URL', 'https://tutorcraft.example');
    expect(indexingAllowed()).toBe(true);
    vi.stubEnv('SEO_INDEXING', 'false');
    expect(indexingAllowed()).toBe(false);
  });
});

describe('robots presets', () => {
  it('maps presets to index/follow flags on an indexable deployment', () => {
    vi.stubEnv('SITE_URL', 'https://tutorcraft.example');
    expect(robots('public')).toMatchObject({
      index: true,
      follow: true,
      'max-image-preview': 'large',
    });
    expect(robots('unlisted')).toEqual({ index: false, follow: true });
    expect(robots('private')).toEqual({ index: false, follow: false });
  });

  it('turns every preset private when indexing is off', () => {
    vi.stubEnv('SEO_INDEXING', 'false');
    expect(robots('public')).toEqual({ index: false, follow: false });
  });
});

describe('pageMetadata', () => {
  it('builds canonical, Open Graph and Twitter from one input', () => {
    vi.stubEnv('SITE_URL', 'https://tutorcraft.example');
    const metadata = pageMetadata({
      path: '/c/demo/',
      title: 'Курсы',
      description: 'Описание',
      locale: 'ru',
      imagePath: '/c/demo/opengraph-image',
    });
    expect(metadata.alternates?.canonical).toBe('/c/demo');
    expect(metadata.openGraph).toMatchObject({ url: '/c/demo', locale: 'ru_RU' });
    expect(metadata.twitter).toMatchObject({ card: 'summary_large_image' });
  });
});

describe('excerpt', () => {
  it('collapses whitespace and cuts on a word boundary', () => {
    expect(excerpt('  один   два  ')).toBe('один два');
    const long = `${'слово '.repeat(40)}`;
    const cut = excerpt(long, 50);
    expect(length(cut)).toBeLessThanOrEqual(50);
    expect(cut.endsWith('слово…')).toBe(true);
  });
});

describe('structured data', () => {
  const origin = 'https://tutorcraft.example';

  it('drops a single-item breadcrumb and null nodes', () => {
    expect(breadcrumbNode(`${origin}/`, [{ name: 'Главная', url: `${origin}/` }])).toBeNull();
    expect(graph(null, undefined, { '@type': 'Thing' })['@graph']).toHaveLength(1);
  });

  it('references the breadcrumb only when asked to', () => {
    const page = webPageNode(origin, {
      url: `${origin}/`,
      name: 'n',
      description: 'd',
      locale: 'ru',
    });
    expect(page).not.toHaveProperty('breadcrumb');
  });

  it('marks storefront courses as free online courses', () => {
    const course = courseNode(origin, {
      url: `${origin}/c/demo/algebra`,
      name: 'Алгебра',
      description: '',
      locale: 'ru',
      tenantSlug: 'demo',
      teacherName: '',
      imageUrl: `${origin}/c/demo/algebra/opengraph-image`,
      moduleTitles: [],
    });
    expect(course).toMatchObject({
      description: 'Алгебра',
      isAccessibleForFree: true,
      offers: { price: 0, category: 'Free' },
      hasCourseInstance: { courseMode: 'Online' },
    });
    expect(course).not.toHaveProperty('instructor');
  });

  it('cannot be broken out of its script tag', () => {
    const json = serializeJsonLd(
      graph({ '@type': 'Thing', name: '</script><script>alert(1)</script>' }),
    );
    expect(json).not.toContain('</script>');
    expect(JSON.parse(json)['@graph'][0].name).toBe('</script><script>alert(1)</script>');
  });
});
