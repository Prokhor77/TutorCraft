import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import { renderWithProviders } from '@/test/render';
import { EventDetailsDialog } from './event-details-dialog';
import { EventFormDialog } from './event-form-dialog';

const api = vi.hoisted(() => ({
  createNote: vi.fn(),
  createLesson: vi.fn(),
  students: vi.fn(),
}));

vi.mock('@/lib/api/endpoints/calendar', () => ({ calendarApi: api }));
vi.mock('@/lib/api/endpoints/courses', () => ({
  coursesApi: { outline: () => Promise.resolve({ courseId: 'c1', modules: [] }) },
}));

const COURSE = { id: 'c1', title: 'Математика' };
const DAY = new Date(2026, 9, 5);

describe('calendar dialogs', () => {
  it('creates an all-day note', async () => {
    api.createNote.mockResolvedValue({});
    const onClose = vi.fn();
    renderWithProviders(<EventFormDialog target={{ date: DAY }} courses={[]} onClose={onClose} />);

    expect(screen.queryByRole('radiogroup', { name: 'Тип события' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Название/), 'Контрольная');
    await userEvent.click(screen.getByRole('switch'));
    await userEvent.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(api.createNote).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Контрольная', allDay: true, endsAt: null }),
    );
  });

  it('requires chosen students for a lesson addressed to selected students', async () => {
    api.students.mockResolvedValue([
      { id: 's1', firstName: 'Анна', lastName: 'Иванова', email: 'a@example.com' },
    ]);
    renderWithProviders(
      <EventFormDialog
        target={{ date: DAY, courseId: 'c1' }}
        courses={[COURSE]}
        onClose={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('radio', { name: 'Занятие для учеников' }));
    await userEvent.type(screen.getByLabelText(/Название/), 'Разбор ДЗ');
    await userEvent.click(screen.getByLabelText('Выбранным ученикам'));
    expect(await screen.findByText('Анна Иванова')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Создать' }));

    expect(await screen.findByText('Выберите хотя бы одного ученика')).toBeInTheDocument();
    expect(api.createLesson).not.toHaveBeenCalled();
  });

  it('shows a lesson to a student without edit actions', () => {
    const lesson = {
      id: 'l1',
      title: 'Разбор ДЗ',
      startsAt: '2026-10-05T15:00:00Z',
      endsAt: '2026-10-05T16:00:00Z',
      courseId: 'c1',
      itemId: null,
      kind: 'lesson',
      description: 'Принести тетрадь',
      allDay: false,
      courseTitle: 'Математика',
      moduleTitle: 'Дроби',
      attendeeIds: [],
      canEdit: false,
    } as CalendarEvent;
    renderWithProviders(<EventDetailsDialog event={lesson} onClose={vi.fn()} onEdit={vi.fn()} />);

    expect(screen.getByRole('link', { name: 'Математика' })).toBeInTheDocument();
    expect(screen.getByText('Дроби')).toBeInTheDocument();
    expect(screen.getByText('Принести тетрадь')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Изменить/ })).not.toBeInTheDocument();
  });
});
