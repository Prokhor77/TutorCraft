'use client';
import { useQuery } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { filesApi, uploadFile } from '@/lib/api/endpoints/files';
import type { FileMeta, FilePurpose } from '@/lib/api/schemas/files';
import { MS_PER_MINUTE } from '@/lib/utils/time';
import { useProblemToast } from '../app/use-problem-toast';
import { queryKeys } from '../query-keys';

/** Pre-signed GET URLs live 10 minutes (contract §7) — refresh well before expiry. */
const FILE_URL_STALE_MS = 5 * MS_PER_MINUTE;
const VIDEO_PROCESSING_POLL_MS = 10_000;

export function useFileMeta(
  fileId: string | null | undefined,
  options: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: queryKeys.file(fileId ?? 'none'),
    queryFn: () => filesApi.get(fileId as string),
    enabled: !!fileId && (options.enabled ?? true),
    staleTime: FILE_URL_STALE_MS,
    refetchInterval: (query) =>
      query.state.data?.video?.status === 'processing'
        ? VIDEO_PROCESSING_POLL_MS
        : FILE_URL_STALE_MS,
  });
}

export type UploadState = { name: string; progress: number };

/** Presigned upload flow with progress and error toasts (FR-CONTENT-01/02). */
export function useFileUpload(purpose: FilePurpose) {
  const showProblem = useProblemToast();
  const [uploads, setUploads] = useState<UploadState[]>([]);

  const upload = useCallback(
    async (file: File): Promise<FileMeta | null> => {
      setUploads((current) => [...current, { name: file.name, progress: 0 }]);
      const setProgress = (progress: number) =>
        setUploads((current) =>
          current.map((entry) => (entry.name === file.name ? { ...entry, progress } : entry)),
        );
      try {
        return await uploadFile(file, purpose, setProgress);
      } catch (error) {
        showProblem(error);
        return null;
      } finally {
        setUploads((current) => current.filter((entry) => entry.name !== file.name));
      }
    },
    [purpose, showProblem],
  );

  return { upload, uploads, isUploading: uploads.length > 0 };
}

/** Opens a file through its fresh pre-signed URL (the download endpoint needs a bearer token). */
export async function openFile(fileId: string): Promise<void> {
  const meta = await filesApi.get(fileId);
  if (meta.url) window.open(meta.url, '_blank', 'noopener,noreferrer');
}
