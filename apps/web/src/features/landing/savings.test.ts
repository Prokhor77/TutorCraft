import { describe, expect, it } from 'vitest';
import { LANDING } from '@/content/landing';
import { calculateSavings, clampStudents, declension, roundHours } from './savings';

const STUDENT = { one: 'ученик', few: 'ученика', many: 'учеников', other: 'ученика' };

describe('landing savings calculator', () => {
  it('matches the default estimate: 22 students → 18.7 h/week, ≈ 2 240 BYN/month', () => {
    const result = calculateSavings(22, LANDING.savings);
    expect(result.hoursPerWeek).toBe(18.7);
    expect(result.grading).toBe(9.2);
    expect(result.quizzes).toBe(5.5);
    expect(result.messaging).toBe(4);
    expect(result.moneyPerMonth).toBe(2240);
  });

  it('clamps the slider range and rounds counts', () => {
    expect(clampStudents(1, LANDING.savings)).toBe(5);
    expect(clampStudents(200, LANDING.savings)).toBe(60);
    expect(clampStudents(12.6, LANDING.savings)).toBe(13);
    expect(clampStudents(Number.NaN, LANDING.savings)).toBe(22);
    expect(calculateSavings(5, LANDING.savings).hoursPerWeek).toBe(4.3);
  });

  it('rounds hours to one decimal', () => {
    expect(roundHours(0.1 + 0.2)).toBe(0.3);
  });

  it('declines Russian nouns by CLDR rules', () => {
    expect(declension(1, STUDENT, 'ru')).toBe('ученик');
    expect(declension(2, STUDENT, 'ru')).toBe('ученика');
    expect(declension(5, STUDENT, 'ru')).toBe('учеников');
    expect(declension(11, STUDENT, 'ru')).toBe('учеников');
    expect(declension(21, STUDENT, 'ru')).toBe('ученик');
    expect(declension(22, STUDENT, 'ru')).toBe('ученика');
    expect(declension(1, { one: 'student', other: 'students' }, 'en')).toBe('student');
    expect(declension(22, { one: 'student', other: 'students' }, 'en')).toBe('students');
  });
});
