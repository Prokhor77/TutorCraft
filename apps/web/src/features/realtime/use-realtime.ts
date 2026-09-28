'use client';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';
import { toast } from '@/components/ui/toast';
import { http } from '@/lib/api/http';
import { useAuthStore } from '@/stores/auth-store';
import { COUNTER_NAMES, useUiStore } from '@/stores/ui-store';
import { queryKeys } from '../query-keys';
import {
  AUTH_CLOSE_CODES,
  parseRealtimeMessage,
  reconnectDelay,
  type RealtimeMessage,
} from './messages';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? '';
const GRADING_CATEGORIES = new Set(['submission_received']);

/** API-08: live notifications and counters; reconnects with backoff and refreshes the token on auth close. */
export function useRealtime(): void {
  const queryClient = useQueryClient();
  const router = useRouter();
  const t = useTranslations('notifications');
  const isAuthenticated = useAuthStore((state) => state.status === 'authenticated');
  const handlerRef = useRef<(message: RealtimeMessage) => void>(() => undefined);

  handlerRef.current = (message) => {
    if (message.type === 'counter') {
      useUiStore.getState().setCounter(message.data.name, message.data.value);
      if (message.data.name === COUNTER_NAMES.gradingQueue)
        void queryClient.invalidateQueries({ queryKey: queryKeys.gradingQueueRoot });
      return;
    }
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
    if (GRADING_CATEGORIES.has(message.data.category))
      void queryClient.invalidateQueries({ queryKey: queryKeys.gradingQueueRoot });
    const link = message.data.link;
    toast({
      title: message.data.title,
      description: message.data.body,
      action: link ? { label: t('open'), onClick: () => router.push(link) } : undefined,
    });
  };

  useEffect(() => {
    if (!isAuthenticated || !WS_URL) return;
    let socket: WebSocket | null = null;
    let attempt = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;

    const connect = () => {
      const token = useAuthStore.getState().accessToken;
      if (disposed || !token) return;
      socket = new WebSocket(`${WS_URL}?token=${encodeURIComponent(token)}`);
      socket.onopen = () => {
        attempt = 0;
      };
      socket.onmessage = (event) => {
        const message = typeof event.data === 'string' ? parseRealtimeMessage(event.data) : null;
        if (message) handlerRef.current(message);
      };
      socket.onclose = (event) => {
        if (disposed) return;
        const retry = () => {
          timer = setTimeout(connect, reconnectDelay(attempt));
          attempt += 1;
        };
        if (AUTH_CLOSE_CODES.has(event.code))
          void http.refreshSession().then((outcome) => outcome !== 'expired' && retry());
        else retry();
      };
    };

    connect();
    return () => {
      disposed = true;
      clearTimeout(timer);
      socket?.close();
    };
  }, [isAuthenticated]);
}
