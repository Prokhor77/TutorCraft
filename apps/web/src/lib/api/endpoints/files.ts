import { http } from '../http';
import { ApiProblem } from '../problem';
import { fileMetaSchema, uploadTicketSchema, type FilePurpose } from '../schemas/files';

export type UploadProgress = (fraction: number) => void;

export const filesApi = {
  requestUpload: (body: {
    fileName: string;
    contentType: string;
    size: number;
    purpose: FilePurpose;
  }) => http.request('/files/uploads', { method: 'POST', body, schema: uploadTicketSchema }),
  complete: (id: string) =>
    http.request(`/files/${id}/complete`, { method: 'POST', schema: fileMetaSchema }),
  get: (id: string) => http.request(`/files/${id}`, { schema: fileMetaSchema }),
  downloadUrl: (id: string) => `/api/v1/files/${id}/download`,
};

const UPLOAD_OK_MIN = 200;
const UPLOAD_OK_MAX = 299;

/** PUT to the pre-signed S3 URL with the signed headers; XHR gives upload progress. */
export function putToPresignedUrl(
  url: string,
  headers: Record<string, string>,
  file: Blob,
  onProgress?: UploadProgress,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    for (const [name, value] of Object.entries(headers)) xhr.setRequestHeader(name, value);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total);
    };
    xhr.onload = () => {
      if (xhr.status >= UPLOAD_OK_MIN && xhr.status <= UPLOAD_OK_MAX) return resolve();
      reject(
        new ApiProblem({ status: xhr.status, code: 'files.upload_failed', title: 'Upload failed' }),
      );
    };
    xhr.onerror = () =>
      reject(new ApiProblem({ status: 0, code: 'client.network', title: 'Network error' }));
    xhr.send(file);
  });
}

/** Full presigned flow (FR-CONTENT-01/02): ticket → PUT → complete. */
export async function uploadFile(file: File, purpose: FilePurpose, onProgress?: UploadProgress) {
  const ticket = await filesApi.requestUpload({
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    size: file.size,
    purpose,
  });
  await putToPresignedUrl(ticket.uploadUrl, ticket.headers, file, onProgress);
  return filesApi.complete(ticket.fileId);
}
