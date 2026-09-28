'use client';
import { useEffect, useRef } from 'react';

type HlsPlayerProps = { src: string; title: string; poster?: string; className?: string };

/** HLS playback (ADR-008): native on Safari/iOS, hls.js elsewhere (loaded lazily). */
export function HlsPlayer({ src, title, poster, className }: HlsPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src;
      return;
    }
    let destroyed = false;
    let cleanup: (() => void) | undefined;
    void import('hls.js').then(({ default: Hls }) => {
      if (destroyed || !Hls.isSupported()) return;
      const hls = new Hls();
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) console.warn('[hls] fatal playback error', data.type);
      });
      cleanup = () => hls.destroy();
    });
    return () => {
      destroyed = true;
      cleanup?.();
    };
  }, [src]);

  return (
    <video
      ref={videoRef}
      controls
      playsInline
      preload="metadata"
      poster={poster}
      aria-label={title}
      className={className ?? 'aspect-video w-full rounded bg-black'}
    />
  );
}
