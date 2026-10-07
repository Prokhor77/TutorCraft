import { serializeJsonLd, type JsonLdGraph } from '@/lib/seo/structured-data';

/** Server-rendered JSON-LD. Not executed by the browser, so CSP `script-src` does not apply to it. */
export function JsonLd({ data }: { data: JsonLdGraph }) {
  return (
    <script
      type="application/ld+json"
      // Escaped by serializeJsonLd: `<` becomes \u003c, so the payload cannot close the script tag.
      dangerouslySetInnerHTML={{ __html: serializeJsonLd(data) }}
    />
  );
}
