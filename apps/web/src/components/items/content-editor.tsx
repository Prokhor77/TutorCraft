'use client';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { SaveIndicator } from '@/components/editor/save-indicator';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { useProblemToast } from '@/features/app/use-problem-toast';
import { clearLocalDraft, readLocalDraft, useAutosave } from '@/features/editor/use-autosave';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import { emptyDoc, validateDoc } from '@/lib/blockdoc/doc';

type Props = {
  /** Stable key for the local draft (e.g. `item:<id>:content`). */
  draftKey: string;
  initial: BlockDoc | null;
  save: (doc: BlockDoc) => Promise<unknown>;
  label: string;
};

/** Block editor with autosave every ≤ 10 s and on blur (UX-03), local draft recovery and alt validation. */
export function ContentEditor({ draftKey, initial, save, label }: Props) {
  const t = useTranslations('autosave');
  const showProblem = useProblemToast();
  const [doc, setDoc] = useState<BlockDoc>(initial ?? emptyDoc());
  const [recoverable, setRecoverable] = useState<BlockDoc | null>(null);
  const autosave = useAutosave({
    value: doc,
    save,
    draftKey,
    isValid: (value) => validateDoc(value).length === 0,
    onError: showProblem,
  });

  useEffect(() => {
    const draft = readLocalDraft<BlockDoc>(draftKey);
    if (draft && JSON.stringify(draft.value) !== JSON.stringify(initial ?? emptyDoc()))
      setRecoverable(draft.value);
  }, [draftKey, initial]);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-end">
        <SaveIndicator status={autosave.status} lastSavedAt={autosave.lastSavedAt} />
      </div>
      {recoverable ? (
        <Alert tone="info" title={t('recoverTitle')}>
          <div className="mt-2 flex gap-2">
            <Button
              size="sm"
              onClick={() => {
                setDoc(recoverable);
                setRecoverable(null);
              }}
            >
              {t('recover')}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                clearLocalDraft(draftKey);
                setRecoverable(null);
              }}
            >
              {t('discard')}
            </Button>
          </div>
        </Alert>
      ) : null}
      {autosave.status === 'invalid' ? (
        <Alert tone="warning" title={t('invalidTitle')}>
          {t('invalidText')}
        </Alert>
      ) : null}
      <BlockEditor
        value={doc}
        onChange={setDoc}
        label={label}
        onBlur={() => void autosave.flush()}
      />
    </div>
  );
}
