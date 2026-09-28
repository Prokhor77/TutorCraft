'use client';
import { CloudOff, FileText, Send, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { StatusBadge } from '@/components/course/item-meta';
import { BlockEditor } from '@/components/editor/block-editor';
import { SaveIndicator } from '@/components/editor/save-indicator';
import { FileCard } from '@/components/media/file-preview';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { SkeletonList } from '@/components/ui/skeleton';
import { TableContainer, Table, THead, TBody, TR, TH, TD } from '@/components/ui/table';
import { useProblemToast } from '@/features/app/use-problem-toast';
import {
  submitDedupeKey,
  useMySubmission,
  useSaveDraft,
  useSubmitAssignment,
} from '@/features/assessment/use-assessment';
import { useAutosave } from '@/features/editor/use-autosave';
import { useFileUpload } from '@/features/files/use-files';
import { useOnlineStatus, usePendingOperations } from '@/features/offline/use-offline-runner';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import type { AssignmentSettings, ItemDetail } from '@/lib/api/schemas/courses';
import type { Submission } from '@/lib/api/schemas/assessment';
import { emptyDoc } from '@/lib/blockdoc/doc';
import { formatDateTime, formatScore } from '@/lib/utils/format';
import { DeadlineCountdown } from './countdown';

const EDITABLE_STATUSES = new Set(['draft', 'returned']);

function GradeCard({ submission }: { submission: Submission }) {
  const t = useTranslations('assignment');
  const locale = useLocale();
  const grade = submission.grade;
  if (!grade?.published) return null;
  return (
    <Card className="border-success/40">
      <CardHeader>
        <CardTitle>{t('yourGrade')}</CardTitle>
        <span className="text-2xl font-bold text-success">
          {formatScore(grade.score, grade.maxScore, locale)}
        </span>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {grade.graderName ? (
          <p className="text-xs text-text-muted">
            {t('gradedBy', {
              name: grade.graderName,
              date: grade.gradedAt ? formatDateTime(grade.gradedAt, locale) : '',
            })}
          </p>
        ) : null}
        <BlockRenderer doc={grade.feedback} />
        {grade.feedbackFiles.map((file) => (
          <FileCard key={file.id} meta={file} />
        ))}
      </CardContent>
    </Card>
  );
}

function AttemptsHistory({ submission }: { submission: Submission }) {
  const t = useTranslations('assignment');
  const locale = useLocale();
  if (submission.history.length === 0) return null;
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-lg">{t('history')}</h2>
      <TableContainer>
        <Table>
          <THead>
            <tr>
              <TH>{t('attempt')}</TH>
              <TH>{t('status')}</TH>
              <TH>{t('submittedAt')}</TH>
              <TH>{t('score')}</TH>
            </tr>
          </THead>
          <TBody>
            {submission.history.map((entry) => (
              <TR key={entry.attemptNo}>
                <TD>{entry.attemptNo}</TD>
                <TD>
                  <StatusBadge status={entry.status} />
                </TD>
                <TD>{entry.submittedAt ? formatDateTime(entry.submittedAt, locale) : '—'}</TD>
                <TD>{entry.score ?? '—'}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </TableContainer>
    </section>
  );
}

function SubmissionWorkspace({
  item,
  settings,
  submission,
}: {
  item: ItemDetail;
  settings: AssignmentSettings;
  submission: Submission;
}) {
  const t = useTranslations('assignment');
  const showProblem = useProblemToast();
  const online = useOnlineStatus();
  const saveDraft = useSaveDraft(item.id);
  const { submit, isSubmitting } = useSubmitAssignment(item.id);
  const pending = usePendingOperations(submitDedupeKey(item.id));
  const upload = useFileUpload('submission');
  const [text, setText] = useState<BlockDoc>(submission.text ?? emptyDoc());
  const [files, setFiles] = useState(submission.files);
  const acceptsText = settings.submissionType === 'text' || settings.submissionType === 'both';
  const acceptsFiles = settings.submissionType === 'file' || settings.submissionType === 'both';
  const fileIds = files.map((file) => file.id);

  const autosave = useAutosave({
    value: text,
    enabled: acceptsText,
    draftKey: `submission:${item.id}`,
    save: (value) => saveDraft.mutateAsync({ text: value, fileIds }),
    onError: showProblem,
  });

  useEffect(() => setFiles(submission.files), [submission.files]);

  const persistFiles = (nextFiles: typeof files) => {
    setFiles(nextFiles);
    saveDraft.mutate(
      { text: acceptsText ? text : undefined, fileIds: nextFiles.map((file) => file.id) },
      { onError: (error) => showProblem(error) },
    );
  };

  const tooManyFiles = files.length >= settings.maxFiles;
  const onSubmit = async () => {
    if (acceptsText) await autosave.flush();
    await submit();
  };

  return (
    <div className="flex flex-col gap-4">
      {!online ? (
        <Alert tone="warning" title={t('offlineTitle')}>
          {t('offlineText')}
        </Alert>
      ) : null}
      {pending.length > 0 ? (
        <Alert tone="info" title={t('pendingTitle')}>
          <span className="flex items-center gap-1.5">
            <CloudOff className="size-4" aria-hidden /> {t('pendingText')}
          </span>
        </Alert>
      ) : null}
      {acceptsFiles ? (
        <section className="flex flex-col gap-3" aria-labelledby="files-title">
          <h2 id="files-title" className="text-lg">
            {t('files')}
          </h2>
          <FileDropzone
            title={t('dropFiles')}
            hint={t('fileLimits', {
              max: settings.maxFiles,
              size: settings.maxFileSizeMb,
              types: settings.allowedExtensions.join(', ') || t('anyType'),
            })}
            browseLabel={t('browse')}
            accept={
              settings.allowedExtensions.length
                ? settings.allowedExtensions
                    .map((ext) => (ext.startsWith('.') ? ext : `.${ext}`))
                    .join(',')
                : undefined
            }
            disabled={tooManyFiles || upload.isUploading}
            onFiles={async (selected) => {
              const uploaded = [];
              for (const file of selected.slice(0, settings.maxFiles - files.length)) {
                const meta = await upload.upload(file);
                if (meta) uploaded.push(meta);
              }
              if (uploaded.length) persistFiles([...files, ...uploaded]);
            }}
          />
          {upload.uploads.map((entry) => (
            <p key={entry.name} className="text-sm text-text-muted" aria-live="polite">
              {t('uploading', { name: entry.name, percent: Math.round(entry.progress * 100) })}
            </p>
          ))}
          <ul className="flex flex-col gap-2">
            {files.map((file) => (
              <li key={file.id} className="flex items-center gap-2">
                <div className="flex-1">
                  <FileCard meta={file} />
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t('removeFile', { name: file.name })}
                  onClick={() => persistFiles(files.filter((entry) => entry.id !== file.id))}
                >
                  <Trash2 aria-hidden />
                </Button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {acceptsText ? (
        <section className="flex flex-col gap-2" aria-labelledby="text-title">
          <div className="flex items-center justify-between">
            <h2 id="text-title" className="text-lg">
              {t('answer')}
            </h2>
            <SaveIndicator status={autosave.status} lastSavedAt={autosave.lastSavedAt} />
          </div>
          <BlockEditor
            value={text}
            onChange={setText}
            label={t('answer')}
            uploadPurpose="submission"
            onBlur={() => void autosave.flush()}
          />
        </section>
      ) : null}
      <div className="glass sticky bottom-[calc(var(--size-bottom-nav)+0.5rem)] flex flex-col gap-2 rounded-md border border-card-border p-3 shadow-md sm:flex-row sm:items-center sm:justify-between md:bottom-4">
        <span className="flex items-center gap-2 text-sm text-text-muted">
          <FileText className="size-4" aria-hidden /> {t('draftStatus', { count: files.length })}
        </span>
        <Button
          size="lg"
          onClick={() => void onSubmit()}
          loading={isSubmitting}
          disabled={pending.length > 0 || (acceptsFiles && !acceptsText && files.length === 0)}
        >
          <Send aria-hidden /> {t('submit')}
        </Button>
      </div>
    </div>
  );
}

/** Student assignment screen (FR-ASSIGN-04, SPEC §10 «Сдача задания»). */
export function StudentAssignment({ item }: { item: ItemDetail }) {
  const t = useTranslations('assignment');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const settings = item.settings.kind === 'assignment' ? item.settings : null;
  const mySubmission = useMySubmission(item.id, !!settings && settings.submissionType !== 'none');

  if (!settings) return null;
  const dueAt = mySubmission.data?.dueAt ?? settings.dueAt;
  return (
    <div className="flex flex-col gap-6">
      {dueAt ? <DeadlineCountdown dueAt={dueAt} /> : null}
      {item.content ? (
        <section className="rounded-md border border-card-border bg-surface p-6 shadow-sm md:p-8">
          <BlockRenderer doc={item.content} />
        </section>
      ) : null}
      {settings.submissionType === 'none' ? (
        <Alert tone="info" title={t('offlineAssignment')} />
      ) : null}
      {mySubmission.isLoading ? <SkeletonList label={tCommon('loading')} rows={2} /> : null}
      {mySubmission.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void mySubmission.refetch()}
        />
      ) : null}
      {mySubmission.data ? (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={mySubmission.data.status} />
            {mySubmission.data.late ? (
              <span className="text-xs text-warning">{t('late')}</span>
            ) : null}
            {mySubmission.data.submittedAt ? (
              <span className="text-xs text-text-muted">
                {t('submittedOn', { date: formatDateTime(mySubmission.data.submittedAt, locale) })}
              </span>
            ) : null}
          </div>
          <GradeCard submission={mySubmission.data} />
          {mySubmission.data.status === 'returned' ? (
            <Alert tone="warning" title={t('returnedTitle')}>
              {t('returnedText')}
            </Alert>
          ) : null}
          {EDITABLE_STATUSES.has(mySubmission.data.status) ? (
            <div className="rounded-md border border-card-border bg-surface p-5 shadow-sm md:p-6">
              <SubmissionWorkspace item={item} settings={settings} submission={mySubmission.data} />
            </div>
          ) : (
            <section className="flex flex-col gap-2">
              <h2 className="text-lg">{t('yourWork')}</h2>
              <BlockRenderer doc={mySubmission.data.text} />
              {mySubmission.data.files.map((file) => (
                <FileCard key={file.id} meta={file} />
              ))}
            </section>
          )}
          <AttemptsHistory submission={mySubmission.data} />
        </>
      ) : null}
    </div>
  );
}
