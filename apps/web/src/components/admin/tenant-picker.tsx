'use client';
import { Building2, Check, ChevronsUpDown } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import type { PlatformTenant } from '@/lib/api/endpoints/platform';
import { cn } from '@/lib/utils/cn';

/** School switcher of the platform administrator: every /admin section works inside the chosen school. */
export function TenantPicker({
  tenants,
  value,
  onChange,
}: {
  tenants: readonly PlatformTenant[];
  value: string | null;
  onChange: (tenantId: string) => void;
}) {
  const t = useTranslations('admin.tenant');
  const [open, setOpen] = useState(false);
  const selected = tenants.find((tenant) => tenant.id === value);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="secondary"
          size="sm"
          role="combobox"
          aria-expanded={open}
          aria-label={t('label')}
          className="max-w-full justify-between"
        >
          <Building2 aria-hidden />
          <span className="truncate">{selected?.name ?? t('placeholder')}</span>
          <ChevronsUpDown className="opacity-60" aria-hidden />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-0">
        <Command label={t('label')}>
          <CommandInput placeholder={t('search')} autoFocus />
          <CommandList>
            <CommandEmpty>{t('empty')}</CommandEmpty>
            <CommandGroup>
              {tenants.map((tenant) => (
                <CommandItem
                  key={tenant.id}
                  value={`${tenant.name} ${tenant.slug} ${tenant.id}`}
                  onSelect={() => {
                    setOpen(false);
                    onChange(tenant.id);
                  }}
                >
                  <Check
                    className={cn('size-4 text-primary', tenant.id === value ? 'opacity-100' : 'opacity-0')}
                    aria-hidden
                  />
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate font-medium">{tenant.name}</span>
                    <span className="truncate text-xs text-text-muted">
                      {tenant.slug} · {t('users', { count: tenant.usersCount })}
                      {tenant.status === 'suspended' ? ` · ${t('suspended')}` : ''}
                    </span>
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
