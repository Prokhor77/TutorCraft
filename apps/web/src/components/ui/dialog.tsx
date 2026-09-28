'use client';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

const overlayClass =
  'fixed inset-0 z-50 bg-overlay/40 backdrop-blur-sm data-[state=open]:animate-fade-in';

type ContentProps = React.ComponentProps<typeof DialogPrimitive.Content> & {
  title: ReactNode;
  description?: ReactNode;
  closeLabel: string;
  hideTitle?: boolean;
};

export function DialogContent({
  className,
  title,
  description,
  closeLabel,
  hideTitle,
  children,
  ...props
}: ContentProps) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className={overlayClass} />
      <DialogPrimitive.Content
        className={cn(
          'fixed left-1/2 top-1/2 z-50 flex max-h-[90dvh] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex-col gap-4 overflow-y-auto rounded-lg border border-card-border bg-surface p-7 shadow-lg focus:outline-none data-[state=open]:animate-slide-up',
          className,
        )}
        {...props}
      >
        <div className={cn('flex flex-col gap-1 pr-8', hideTitle && 'sr-only')}>
          <DialogPrimitive.Title className="text-lg font-semibold">{title}</DialogPrimitive.Title>
          {description ? (
            <DialogPrimitive.Description className="text-sm text-text-muted">
              {description}
            </DialogPrimitive.Description>
          ) : null}
        </div>
        {!description ? (
          <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
        ) : null}
        {children}
        <DialogPrimitive.Close
          className="absolute right-4 top-4 rounded-full p-1.5 text-text-muted hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
          aria-label={closeLabel}
        >
          <X className="size-4" aria-hidden />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

export function DialogFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)}
      {...props}
    />
  );
}

/** Sheet / Drawer: side panel (right on desktop, bottom on mobile). */
export function SheetContent({
  className,
  title,
  description,
  closeLabel,
  children,
  ...props
}: ContentProps) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className={overlayClass} />
      <DialogPrimitive.Content
        className={cn(
          'fixed inset-x-0 bottom-0 z-50 flex max-h-[92dvh] flex-col gap-4 overflow-y-auto rounded-t-lg border border-card-border bg-surface p-6 shadow-lg focus:outline-none data-[state=open]:animate-slide-up md:inset-y-0 md:left-auto md:right-0 md:max-h-none md:w-[32rem] md:rounded-none md:rounded-l-lg md:data-[state=open]:animate-slide-in-right',
          className,
        )}
        {...props}
      >
        <div className="flex flex-col gap-1 pr-8">
          <DialogPrimitive.Title className="text-lg font-semibold">{title}</DialogPrimitive.Title>
          <DialogPrimitive.Description
            className={cn('text-sm text-text-muted', !description && 'sr-only')}
          >
            {description ?? title}
          </DialogPrimitive.Description>
        </div>
        {children}
        <DialogPrimitive.Close
          className="absolute right-4 top-4 rounded-full p-1.5 text-text-muted hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
          aria-label={closeLabel}
        >
          <X className="size-4" aria-hidden />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}
export const Sheet = DialogPrimitive.Root;
export const SheetTrigger = DialogPrimitive.Trigger;
