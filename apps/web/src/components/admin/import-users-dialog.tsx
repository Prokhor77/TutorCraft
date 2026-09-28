'use client';
import { FileSpreadsheet } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useUserMutations } from '@/features/admin/use-admin';
import type { ImportCommitResult, ImportPreview } from '@/lib/api/schemas/org';

/** FR-USER-02: CSV import with preview, per-row errors and a commit report. */
export function ImportUsersDialog() {
  const t = useTranslations('adminUsers');
  const tCommon = useTranslations('common');
  const { importPreview, importCommit } = useUserMutations();
  const [open, setOpen] = useState(false);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportCommitResult | null>(null);
  const reset = () => {
    setPreview(null);
    setResult(null);
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        reset();
      }}
    >
      <DialogTrigger asChild>
        <Button variant="secondary">
          <FileSpreadsheet aria-hidden /> {t('import')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('importTitle')}
        description={t('importHint')}
        closeLabel={tCommon('close')}
        className="max-w-3xl"
      >
        {!preview ? (
          <FileDropzone
            title={t('dropCsv')}
            hint={t('csvColumns')}
            browseLabel={t('browse')}
            accept=".csv,text/csv"
            multiple={false}
            disabled={importPreview.isPending}
            onFiles={([file]) => file && importPreview.mutate(file, { onSuccess: setPreview })}
          />
        ) : null}
        {preview && !result ? (
          <div className="flex flex-col gap-3">
            <div className="flex gap-2">
              <Badge tone="success">{t('validRows', { count: preview.valid })}</Badge>
              <Badge tone={preview.invalid ? 'danger' : 'neutral'}>
                {t('invalidRows', { count: preview.invalid })}
              </Badge>
            </div>
            <TableContainer className="max-h-80">
              <Table>
                <THead>
                  <tr>
                    <TH>#</TH>
                    <TH>{t('email')}</TH>
                    <TH>{t('name')}</TH>
                    <TH>{t('course')}</TH>
                    <TH>{t('errors')}</TH>
                  </tr>
                </THead>
                <TBody>
                  {preview.rows.map((row) => (
                    <TR
                      key={row.row}
                      className={row.errors.length ? 'bg-danger-soft/40' : undefined}
                    >
                      <TD>{row.row}</TD>
                      <TD>{row.email}</TD>
                      <TD>
                        {row.firstName} {row.lastName}
                      </TD>
                      <TD>{row.courseShortName ?? '—'}</TD>
                      <TD className="text-xs text-danger">
                        {row.errors.map((error) => `${error.field}: ${error.message}`).join('; ')}
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </TableContainer>
            <DialogFooter>
              <Button variant="secondary" onClick={reset}>
                {t('chooseAnother')}
              </Button>
              <Button
                disabled={preview.valid === 0}
                loading={importCommit.isPending}
                onClick={() => importCommit.mutate(preview.previewId, { onSuccess: setResult })}
              >
                {t('commit', { count: preview.valid })}
              </Button>
            </DialogFooter>
          </div>
        ) : null}
        {result ? (
          <div className="flex flex-col gap-3">
            <Alert
              tone={result.errors.length ? 'warning' : 'success'}
              title={t('importDone', { created: result.created, enrolled: result.enrolled })}
            />
            {result.errors.length > 0 ? (
              <ul className="max-h-48 overflow-y-auto text-sm text-danger">
                {result.errors.map((error) => (
                  <li key={`${error.row}-${error.field}`}>
                    {t('rowError', { row: error.row, field: error.field, message: error.message })}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
