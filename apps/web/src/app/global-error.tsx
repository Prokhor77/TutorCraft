'use client';
import { CloudOff, RotateCcw } from 'lucide-react';
import { CenteredCard, PublicBlobs, StatusMessage } from '@/components/public/status-card';
import { Button } from '@/components/ui/button';
import '@/styles/globals.css';

/** Last-resort boundary (root layout failed): no i18n context available, bilingual static text. */
export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="ru">
      <body>
        <main className="relative flex min-h-dvh items-center justify-center overflow-clip bg-background px-4 py-10 text-text">
          <PublicBlobs />
          <CenteredCard className="max-w-lg">
            <StatusMessage
              icon={CloudOff}
              tone="danger"
              role="alert"
              title="Что-то пошло не так / Something went wrong"
              description="Попробуйте обновить страницу. / Try reloading the page."
              actions={
                <Button size="lg" onClick={reset}>
                  <RotateCcw aria-hidden /> Повторить / Retry
                </Button>
              }
            />
          </CenteredCard>
        </main>
      </body>
    </html>
  );
}
