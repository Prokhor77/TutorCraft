import { describe, expect, it } from 'vitest';
import { courseInvitationResultSchema, platformUserSchema, schoolMemberSchema } from './members';

const base = {
  id: '0190a6f2-7b3c-7d4e-8f00-000000000001',
  email: 'student@school.ru',
  firstName: 'Анна',
  lastName: 'Смирнова',
  status: 'active',
  origin: 'tutor_invite',
  createdBy: {
    id: '0190a6f2-7b3c-7d4e-8f00-000000000002',
    email: 'tutor@school.ru',
    firstName: 'Иван',
    lastName: 'Петров',
  },
  tenantRoles: [],
  lastLoginAt: null,
  createdAt: '2026-09-28T10:00:00Z',
};

describe('member schemas (contract §4.1–4.2, §6.1)', () => {
  it('parses a school member with a platform block flag', () => {
    expect(schoolMemberSchema.parse({ ...base, platformBlocked: true }).platformBlocked).toBe(true);
  });

  it('parses a platform user with school and block details', () => {
    const user = platformUserSchema.parse({
      ...base,
      platformBlock: { at: '2026-09-28T11:00:00Z', reason: null },
      school: { id: '0190a6f2-7b3c-7d4e-8f00-000000000003', slug: 'demo', name: 'Demo' },
    });
    expect(user.school.slug).toBe('demo');
    expect(user.createdBy?.firstName).toBe('Иван');
  });

  it('rejects an unknown account origin', () => {
    expect(() =>
      schoolMemberSchema.parse({ ...base, origin: 'magic', platformBlocked: false }),
    ).toThrow();
  });

  it('accepts an invitation result without activation link for existing accounts', () => {
    const result = courseInvitationResultSchema.parse({
      userId: base.id,
      accountCreated: false,
      activationUrl: null,
    });
    expect(result.activationUrl).toBeNull();
  });
});
