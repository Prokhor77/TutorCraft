import { describe, expect, it } from 'vitest';
import { ROUTES } from '../auth/routes';
import { buildNavigation } from './navigation';

describe('buildNavigation', () => {
  const hrefs = (hints: Parameters<typeof buildNavigation>[0]) =>
    buildNavigation(hints).map((item) => item.href);

  it('shows the school students section only to school owners', () => {
    expect(hrefs({ teaches: true, learns: false, isAdmin: false, ownsSchool: true })).toContain(
      ROUTES.schoolStudents,
    );
    expect(hrefs({ teaches: true, learns: false, isAdmin: false })).not.toContain(
      ROUTES.schoolStudents,
    );
  });

  it('gives the platform administrator the admin console only', () => {
    expect(hrefs({ teaches: true, learns: true, isAdmin: true, ownsSchool: true })).toEqual([
      ROUTES.admin,
    ]);
  });
});
