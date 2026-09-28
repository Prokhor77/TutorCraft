'use client';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { COMPACT_BLOCK_KINDS } from '@/components/editor/block-kinds';
import { Button } from '@/components/ui/button';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import { emptyDoc, isDocEmpty, validateDoc } from '@/lib/blockdoc/doc';

type Props = {
  label: string;
  submitLabel: string;
  initial?: BlockDoc;
  onSubmit: (doc: BlockDoc) => Promise<unknown>;
  onCancel?: () => void;
};

export function PostComposer({ label, submitLabel, initial, onSubmit, onCancel }: Props) {
  const tCommon = useTranslations('common');
  const [doc, setDoc] = useState<BlockDoc>(initial ?? emptyDoc());
  const [busy, setBusy] = useState(false);
  const invalid = isDocEmpty(doc) || validateDoc(doc).length > 0;
  return (
    <div className="flex flex-col gap-2 rounded-md border border-card-border bg-surface p-3">
      <BlockEditor value={doc} onChange={setDoc} label={label} kinds={COMPACT_BLOCK_KINDS} />
      <div className="flex justify-end gap-2">
        {onCancel ? (
          <Button variant="ghost" size="sm" onClick={onCancel}>
            {tCommon('cancel')}
          </Button>
        ) : null}
        <Button
          size="sm"
          disabled={invalid}
          loading={busy}
          onClick={() => {
            setBusy(true);
            onSubmit(doc)
              .then(() => setDoc(emptyDoc()))
              .catch(() => undefined)
              .finally(() => setBusy(false));
          }}
        >
          {submitLabel}
        </Button>
      </div>
    </div>
  );
}
