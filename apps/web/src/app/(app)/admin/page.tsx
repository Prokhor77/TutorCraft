import { redirect } from 'next/navigation';
import { ROUTES } from '@/features/auth/routes';

export default function AdminIndex() {
  redirect(ROUTES.adminSchools);
}
