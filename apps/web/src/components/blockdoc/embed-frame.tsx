const EMBED_SANDBOX = 'allow-scripts allow-same-origin allow-presentation allow-popups';

/** Sandboxed iframe for whitelisted embeds (server enforces tenant embedWhitelist). */
export function EmbedFrame({ url, title }: { url: string; title: string }) {
  if (!/^https:\/\//i.test(url)) return null;
  return (
    <iframe
      src={url}
      title={title}
      sandbox={EMBED_SANDBOX}
      loading="lazy"
      referrerPolicy="strict-origin-when-cross-origin"
      allow="fullscreen; picture-in-picture"
      className="aspect-video w-full rounded border border-border"
    />
  );
}
