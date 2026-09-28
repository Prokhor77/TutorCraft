import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { isApiProblem } from '@/lib/api/problem';

/**
 * Maps Problem `errors[]` (RFC 9457 + contract) onto react-hook-form fields.
 * Returns true when at least one field error was applied (caller then skips the generic toast).
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
): boolean {
  if (!isApiProblem(error) || error.errors.length === 0) return false;
  let applied = false;
  for (const fieldError of error.errors) {
    if (!knownFields.includes(fieldError.field)) continue;
    setError(fieldError.field as Path<T>, { type: 'server', message: fieldError.message });
    applied = true;
  }
  return applied;
}
