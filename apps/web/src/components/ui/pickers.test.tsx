import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '@/test/render';
import { DatePicker, formatDateValue, parseDateValue } from './date-picker';
import { TimePicker, timeSlots } from './time-picker';

function DateHarness({ initial }: { initial: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <DatePicker value={value} onChange={setValue} placeholder="Дата" todayLabel="Сегодня" />
      <output>{value}</output>
    </>
  );
}

function TimeHarness({ initial }: { initial: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <TimePicker
        value={value}
        onChange={setValue}
        placeholder="Время"
        clearLabel="Без окончания"
      />
      <output>{value || 'empty'}</output>
    </>
  );
}

describe('date and time pickers', () => {
  it('keeps YYYY-MM-DD in the local calendar', () => {
    const date = parseDateValue('2026-10-05');
    expect(date?.getDate()).toBe(5);
    expect(date && formatDateValue(date)).toBe('2026-10-05');
    expect(parseDateValue('05.10.2026')).toBeUndefined();
  });

  it('builds 15-minute slots and keeps an off-grid value', () => {
    const slots = timeSlots(15, '09:07');
    expect(slots).toHaveLength(97);
    expect(slots.slice(36, 39)).toEqual(['09:00', '09:07', '09:15']);
  });

  it('picks a day from the popover calendar', async () => {
    renderWithProviders(<DateHarness initial="2026-10-05" />);

    await userEvent.click(screen.getByRole('button', { name: /5 октября 2026/ }));
    await userEvent.click(screen.getByRole('button', { name: /15 октября 2026/ }));

    expect(screen.getByRole('status')).toHaveTextContent('2026-10-15');
  });

  it('picks and clears a time', async () => {
    renderWithProviders(<TimeHarness initial="10:00" />);

    await userEvent.click(screen.getByRole('button', { name: /10:00/ }));
    expect(screen.getByRole('option', { name: '10:00' })).toHaveAttribute('aria-selected', 'true');
    await userEvent.click(screen.getByRole('option', { name: '10:30' }));
    expect(screen.getByRole('status')).toHaveTextContent('10:30');

    await userEvent.click(screen.getByRole('button', { name: /10:30/ }));
    await userEvent.click(screen.getByRole('button', { name: /Без окончания/ }));
    expect(screen.getByRole('status')).toHaveTextContent('empty');
  });
});
