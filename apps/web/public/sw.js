/*
 * TutorCraft service worker (NFR-COMP-02): offline access to previously opened course pages/materials.
 * - static assets (/_next/static, /icons, fonts): cache-first (immutable, content-hashed)
 * - learning content API GETs: network-first, fall back to the last cached response
 * - page navigations: network-first, fall back to the cached page
 * Never cached: auth endpoints, non-GET requests, cross-origin requests (pre-signed file URLs).
 * The app posts `tc:clear-user-cache` on logout so cached private data never outlives the session.
 */
const VERSION = 'v1';
const STATIC_CACHE = `tc-static-${VERSION}`;
const CONTENT_CACHE = `tc-content-${VERSION}`;
const PAGE_CACHE = `tc-pages-${VERSION}`;
const KNOWN_CACHES = [STATIC_CACHE, CONTENT_CACHE, PAGE_CACHE];
const PRECACHE = ['/manifest.webmanifest', '/icons/icon.svg', '/icons/icon-192.png'];
const MAX_CONTENT_ENTRIES = 200;

const STATIC_PATTERN = /^\/(_next\/static|icons)\//;
const CONTENT_API_PATTERN = /^\/api\/v1\/(courses\/[^/]+(\/outline|\/completion\/me)?|items\/[^/]+|me\/(tasks|courses)|files\/[^/]+|discussions\/[^/]+)$/;
const NEVER_CACHE_PATTERN = /^\/api\/v1\/auth\//;

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(STATIC_CACHE).then((cache) => cache.addAll(PRECACHE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((name) => !KNOWN_CACHES.includes(name)).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'tc:clear-user-cache') {
    event.waitUntil(Promise.all([caches.delete(CONTENT_CACHE), caches.delete(PAGE_CACHE)]));
  }
});

async function trimCache(cacheName, maxEntries) {
  const cache = await caches.open(cacheName);
  const keys = await cache.keys();
  await Promise.all(keys.slice(0, Math.max(0, keys.length - maxEntries)).map((key) => cache.delete(key)));
}

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) (await caches.open(STATIC_CACHE)).put(request, response.clone());
  return response;
}

async function networkFirst(request, cacheName, cacheKey) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      await cache.put(cacheKey, response.clone());
      if (cacheName === CONTENT_CACHE) trimCache(CONTENT_CACHE, MAX_CONTENT_ENTRIES);
    }
    return response;
  } catch (error) {
    const cached = await caches.match(cacheKey);
    if (cached) return cached;
    throw error;
  }
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || NEVER_CACHE_PATTERN.test(url.pathname)) return;

  if (STATIC_PATTERN.test(url.pathname)) {
    event.respondWith(cacheFirst(request));
    return;
  }
  if (CONTENT_API_PATTERN.test(url.pathname)) {
    // Cache key without auth header variance: URL only (cleared on logout).
    event.respondWith(networkFirst(request, CONTENT_CACHE, new Request(url.toString())));
    return;
  }
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request, PAGE_CACHE, new Request(url.pathname)));
  }
});
