'use client';
import { useEffect, useRef } from 'react';
import type { TelegramAuthPayload } from '@/lib/api/schemas/auth';

const TELEGRAM_WIDGET_SRC = 'https://telegram.org/js/telegram-widget.js?22';
const TELEGRAM_OAUTH_ORIGIN = 'https://oauth.telegram.org';

/**
 * Telegram Login Widget (FR-AUTH-HYB-01). The widget's iframe posts signed user data to the page. We listen for that
 * message ourselves instead of using `data-onauth`: the widget turns that attribute into a function via `eval`, which
 * our CSP (no 'unsafe-eval') blocks.
 */
export function TelegramButton({
  botUsername,
  onAuth,
}: {
  botUsername: string;
  onAuth: (payload: TelegramAuthPayload) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onAuth);
  callbackRef.current = onAuth;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const onMessage = (event: MessageEvent) => {
      if (event.origin !== TELEGRAM_OAUTH_ORIGIN) return;
      const iframe = container.querySelector('iframe');
      if (!iframe || event.source !== iframe.contentWindow) return;
      let data: { event?: string; auth_data?: TelegramAuthPayload } | null = null;
      try {
        data = typeof event.data === 'string' ? JSON.parse(event.data) : null;
      } catch {
        return;
      }
      if (data?.event === 'auth_user' && data.auth_data) callbackRef.current(data.auth_data);
    };
    window.addEventListener('message', onMessage);
    const script = document.createElement('script');
    script.src = TELEGRAM_WIDGET_SRC;
    script.async = true;
    script.setAttribute('data-telegram-login', botUsername);
    script.setAttribute('data-size', 'large');
    script.setAttribute('data-radius', '8');
    script.setAttribute('data-request-access', 'write');
    container.appendChild(script);
    return () => {
      window.removeEventListener('message', onMessage);
      container.innerHTML = '';
    };
  }, [botUsername]);

  return <div ref={containerRef} className="flex min-h-10 justify-center" />;
}
