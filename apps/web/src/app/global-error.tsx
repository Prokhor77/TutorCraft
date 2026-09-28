'use client';

/** Last-resort boundary (root layout failed): no i18n context available, bilingual static text. */
export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="ru">
      <body style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem' }}>
        <h1>Что-то пошло не так / Something went wrong</h1>
        <button type="button" onClick={reset}>
          Повторить / Retry
        </button>
      </body>
    </html>
  );
}
