'use client';
import Script from 'next/script';
import { useLocale } from 'next-intl';
import { useEffect, useRef, useState } from 'react';

type GoogleCredentialResponse = { credential: string };
type GoogleIdentity = {
  accounts: {
    id: {
      initialize: (config: {
        client_id: string;
        callback: (response: GoogleCredentialResponse) => void;
        ux_mode?: string;
      }) => void;
      renderButton: (element: HTMLElement, options: Record<string, unknown>) => void;
    };
  };
};
declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

const GSI_SRC = 'https://accounts.google.com/gsi/client';
const BUTTON_WIDTH_PX = 320;

/** Google Identity Services button (FR-AUTH-HYB-01). Rendered only when /auth/providers returns a clientId. */
export function GoogleButton({
  clientId,
  onCredential,
}: {
  clientId: string;
  onCredential: (idToken: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [loaded, setLoaded] = useState(false);
  const locale = useLocale();
  const callbackRef = useRef(onCredential);
  callbackRef.current = onCredential;

  useEffect(() => {
    if (!loaded || !window.google || !containerRef.current) return;
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => callbackRef.current(response.credential),
    });
    window.google.accounts.id.renderButton(containerRef.current, {
      theme: 'outline',
      size: 'large',
      width: BUTTON_WIDTH_PX,
      locale,
      text: 'continue_with',
    });
  }, [loaded, clientId, locale]);

  return (
    <>
      <Script
        src={GSI_SRC}
        strategy="afterInteractive"
        onLoad={() => setLoaded(true)}
        onReady={() => setLoaded(true)}
      />
      <div ref={containerRef} className="flex min-h-10 justify-center" />
    </>
  );
}
