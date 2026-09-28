'use client';
import * as Menu from '@radix-ui/react-dropdown-menu';
import { cn } from '@/lib/utils/cn';

export const DropdownMenu = Menu.Root;
export const DropdownMenuTrigger = Menu.Trigger;
export const DropdownMenuGroup = Menu.Group;

export function DropdownMenuContent({
  className,
  sideOffset = 6,
  ...props
}: React.ComponentProps<typeof Menu.Content>) {
  return (
    <Menu.Portal>
      <Menu.Content
        sideOffset={sideOffset}
        className={cn(
          'z-50 max-h-[var(--radix-dropdown-menu-content-available-height)] min-w-48 overflow-y-auto rounded border border-card-border bg-surface p-1.5 shadow-lg data-[state=open]:animate-fade-in',
          className,
        )}
        {...props}
      />
    </Menu.Portal>
  );
}

export function DropdownMenuItem({
  className,
  tone,
  ...props
}: React.ComponentProps<typeof Menu.Item> & { tone?: 'danger' }) {
  return (
    <Menu.Item
      className={cn(
        'flex cursor-pointer select-none items-center gap-2 rounded-full px-3 py-2 text-sm outline-none data-[disabled]:pointer-events-none data-[highlighted]:bg-accent/10 data-[highlighted]:text-primary data-[disabled]:opacity-50 [&_svg]:size-4 [&_svg]:text-text-muted',
        tone === 'danger' && 'text-danger [&_svg]:text-danger',
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuLabel({
  className,
  ...props
}: React.ComponentProps<typeof Menu.Label>) {
  return (
    <Menu.Label
      className={cn('px-2.5 py-1.5 text-xs font-medium text-text-muted', className)}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({
  className,
  ...props
}: React.ComponentProps<typeof Menu.Separator>) {
  return <Menu.Separator className={cn('my-1 h-px bg-border', className)} {...props} />;
}
