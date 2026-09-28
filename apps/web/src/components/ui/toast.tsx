'use client';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { create } from 'zustand';
import { cn } from '@/lib/utils/cn';

export type ToastTone = 'info' | 'success' | 'error';
export type ToastAction = { label: string; onClick: () => void };
export type ToastInput = {
  title: string;
  description?: string;
  tone?: ToastTone;
  action?: ToastAction;
  durationMs?: number;
};
type ToastEntry = ToastInput & { id: number };

export const TOAST_DURATION_MS = 5000;
/** UX-06: undo window for reversible deletes. */
export const UNDO_TOAST_DURATION_MS = 8000;
const MAX_VISIBLE_TOASTS = 4;

type ToastState = {
  toasts: ToastEntry[];
  push: (toast: ToastInput) => void;
  dismiss: (id: number) => void;
};
let nextToastId = 1;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (toast) =>
    set((state) => ({
      toasts: [...state.toasts, { ...toast, id: nextToastId++ }].slice(-MAX_VISIBLE_TOASTS),
    })),
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),
}));

/** Imperative API usable from hooks and mutation callbacks. */
export function toast(input: ToastInput): void {
  useToastStore.getState().push(input);
}

const toneIcon = { info: Info, success: CheckCircle2, error: AlertCircle } as const;
const toneClass = { info: 'text-info', success: 'text-success', error: 'text-danger' } as const;

export function Toaster({ closeLabel }: { closeLabel: string }) {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);
  return (
    <ToastPrimitive.Provider swipeDirection="right">
      {toasts.map((entry) => {
        const tone = entry.tone ?? 'info';
        const Icon = toneIcon[tone];
        return (
          <ToastPrimitive.Root
            key={entry.id}
            duration={
              entry.durationMs ?? (entry.action ? UNDO_TOAST_DURATION_MS : TOAST_DURATION_MS)
            }
            onOpenChange={(open) => !open && dismiss(entry.id)}
            type={tone === 'error' ? 'foreground' : 'background'}
            className="pointer-events-auto flex w-full items-start gap-3 rounded-md border border-border bg-surface p-4 shadow-lg data-[state=open]:animate-slide-up"
          >
            <Icon className={cn('mt-0.5 size-5 shrink-0', toneClass[tone])} aria-hidden />
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <ToastPrimitive.Title className="text-sm font-semibold">
                {entry.title}
              </ToastPrimitive.Title>
              {entry.description ? (
                <ToastPrimitive.Description className="text-sm text-text-muted">
                  {entry.description}
                </ToastPrimitive.Description>
              ) : null}
            </div>
            {entry.action ? (
              <ToastPrimitive.Action
                altText={entry.action.label}
                onClick={entry.action.onClick}
                className="shrink-0 rounded-sm px-2 py-1 text-sm font-semibold text-primary hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                {entry.action.label}
              </ToastPrimitive.Action>
            ) : null}
            <ToastPrimitive.Close
              aria-label={closeLabel}
              className="shrink-0 rounded-sm p-1 text-text-muted hover:text-text"
            >
              <X className="size-4" aria-hidden />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        );
      })}
      <ToastPrimitive.Viewport className="fixed bottom-[calc(var(--size-bottom-nav)+0.5rem)] right-0 z-[60] flex w-full max-w-sm flex-col gap-2 p-4 md:bottom-0" />
    </ToastPrimitive.Provider>
  );
}
