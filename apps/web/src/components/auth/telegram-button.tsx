'use client';
import { useEffect, useRef } from 'react';
import type { TelegramAuthPayload } from '@/lib/api/schemas/auth';

const TELEGRAM_WIDGET_SRC = 'https://telegram.org/js/telegram-widget.js?22';
const CALLBACK_NAME = 'tcOnTelegramAuth';

declare global {
  interface Window {
    [CALLBACK_NAME]?: (user: TelegramAuthPayload) => void;
  }
}

/** Telegram Login Widget (FR-AUTH-HYB-01). The widget calls a global callback with signed user data. */
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
    window[CALLBACK_NAME] = (user) => callbackRef.current(user);
    const script = document.createElement('script');
    script.src = TELEGRAM_WIDGET_SRC;
    script.async = true;
    script.setAttribute('data-telegram-login', botUsername);
    script.setAttribute('data-size', 'large');
    script.setAttribute('data-radius', '8');
    script.setAttribute('data-request-access', 'write');
    script.setAttribute('data-onauth', `${CALLBACK_NAME}(user)`);
    container.appendChild(script);
    return () => {
      container.innerHTML = '';
      delete window[CALLBACK_NAME];
    };
  }, [botUsername]);

  return <div ref={containerRef} className="flex min-h-10 justify-center" />;
}
