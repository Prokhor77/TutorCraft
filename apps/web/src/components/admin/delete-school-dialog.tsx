'use client';
import { Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { useDeleteTenant } from '@/features/admin/use-admin';

/** What the dialog needs to know about the school; the owner is shown so the admin sees whose account goes too. */
export type SchoolToDelete = {
  id: string;
  slug: string;
  name: string;
  ownerEmail?: string | null;
};

/**
 * Irreversible deletion of a school with every account in it — the owner it is registered to included — and all
 * courses, grades and files. The admin confirms by typing the school address (slug).
 */
export function DeleteSchoolDialog({
  school,
  onClose,
}: {
  school: SchoolToDelete | null;
  onClose: () => void;
}) {
  const t = useTranslations('admin.deleteSchool');
  const tCommon = useTranslations('common');
  const [confirmation, setConfirmation] = useState('');
  const remove = useDeleteTenant();
  const close = () => {
    setConfirmation('');
    remove.reset();
    onClose();
  };
  const confirmed = !!school && confirmation.trim() === school.slug;
  return (
    <Dialog open={!!school} onOpenChange={(open) => !open && close()}>
      <DialogContent title={t('title', { name: school?.name ?? '' })} closeLabel={tCommon('close')}>
        <Alert tone="danger" title={t('warningTitle')}>
          {t('warning')}
          {school?.ownerEmail ? (
            <span className="mt-2 block font-semibold">
              {t('owner', { email: school.ownerEmail })}
            </span>
          ) : null}
        </Alert>
        <Field label={t('confirm', { slug: school?.slug ?? '' })}>
          <Input
            value={confirmation}
            autoComplete="off"
            spellCheck={false}
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </Field>
        <DialogFooter>
          <Button variant="secondary" onClick={close}>
            {tCommon('cancel')}
          </Button>
          <Button
            variant="danger"
            disabled={!confirmed}
            loading={remove.isPending}
            onClick={() =>
              school &&
              remove.mutate(
                { tenantId: school.id, confirmSlug: school.slug },
                {
                  onSuccess: () => {
                    toast({ tone: 'success', title: t('deletedToast', { name: school.name }) });
                    close();
                  },
                },
              )
            }
          >
            <Trash2 aria-hidden /> {t('submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
