import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BookOpen } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { CreateCourseDialog } from '@/components/course/create-course-dialog';
import { LockedReason, StatusBadge } from '@/components/course/item-meta';
import { TaskRow } from '@/components/dashboard/task-row';
import { BlockEditor } from '@/components/editor/block-editor';
import { SaveIndicator } from '@/components/editor/save-indicator';
import { ShortcutsHelp } from '@/components/grading/shortcuts-help';
import { QuestionInput } from '@/components/quiz/question-input';
import { QuizTimer } from '@/components/quiz/quiz-timer';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Input } from '@/components/ui/input';
import type { StudentQuestionView } from '@/lib/api/schemas/quiz';
import { renderWithProviders } from '../render';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/courses',
  useSearchParams: () => new URLSearchParams(),
}));

/** jsdom has no layout engine: contrast is enforced by the token palette, not testable here. */
const AXE_OPTIONS = { rules: { 'color-contrast': { enabled: false } } };

async function expectAccessible(container: HTMLElement) {
  expect(await axe(container, AXE_OPTIONS)).toHaveNoViolations();
}

const question = (overrides: Partial<StudentQuestionView>): StudentQuestionView => ({
  slot: 1,
  page: 0,
  points: 1,
  type: 'single_choice',
  title: 'Столица Франции?',
  body: { schemaVersion: 1, blocks: [] },
  response: null,
  flagged: false,
  ...overrides,
});

describe('a11y smoke (NFR-A11Y-01, WCAG 2.2 AA)', () => {
  it('form field wires label, hint and error', async () => {
    const { container } = renderWithProviders(
      <form>
        <Field label="Email" hint="Рабочая почта" error="Введите корректный email" required>
          <Input type="email" />
        </Field>
        <Button type="submit">Отправить</Button>
      </form>,
    );
    const input = screen.getByLabelText(/Email/);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Рабочая почта Введите корректный email');
    await expectAccessible(container);
  });

  it('empty state and file dropzone', async () => {
    const { container } = renderWithProviders(
      <>
        <EmptyState
          icon={BookOpen}
          title="Курсов пока нет"
          description="Создайте первый курс"
          action={<Button>Создать</Button>}
        />
        <FileDropzone
          title="Перетащите файлы"
          browseLabel="Выбрать файлы"
          onFiles={() => undefined}
        />
      </>,
    );
    expect(screen.getByLabelText('Выбрать файлы')).toHaveAttribute('type', 'file');
    await expectAccessible(container);
  });

  it('status badges, locked reason (UX-07) and task rows', async () => {
    const { container } = renderWithProviders(
      <ul>
        <li>
          <TaskRow
            task={{
              itemId: 'i1',
              courseId: 'c1',
              courseTitle: 'Математика',
              itemTitle: 'ДЗ 1',
              itemType: 'assignment',
              dueAt: '2026-10-01T10:00:00Z',
              status: 'draft',
            }}
          />
        </li>
        <li>
          <StatusBadge status="graded" />
          <LockedReason reasons={['оценка за «ДЗ 1» не ниже 60%']} />
        </li>
      </ul>,
    );
    expect(screen.getByText(/Откроется, когда:/)).toBeInTheDocument();
    await expectAccessible(container);
  });

  it('quiz answer controls, timer and save indicator', async () => {
    const { container } = renderWithProviders(
      <div>
        <QuizTimer remaining={65_000} />
        <QuestionInput
          question={question({
            options: [
              { id: 'a', text: 'Париж' },
              { id: 'b', text: 'Лион' },
            ],
          })}
          response={{ optionId: 'a' }}
          onChange={() => undefined}
        />
        <QuestionInput
          question={question({
            slot: 2,
            type: 'ordering',
            items: [
              { id: 'x', text: 'Первый' },
              { id: 'y', text: 'Второй' },
            ],
          })}
          response={null}
          onChange={() => undefined}
        />
        <QuestionInput
          question={question({
            slot: 3,
            type: 'matching',
            prompts: [{ id: 'p', text: 'H2O' }],
            answerChoices: ['Вода'],
          })}
          response={null}
          onChange={() => undefined}
        />
        <SaveIndicator status="saved" lastSavedAt={new Date('2026-09-27T12:00:00Z')} />
      </div>,
    );
    expect(screen.getByRole('radio', { name: 'Париж' })).toBeChecked();
    expect(screen.getByRole('timer')).toHaveTextContent('1:05');
    await expectAccessible(container);
  });

  it('block editor exposes labelled text boxes and toolbar', async () => {
    const { container } = renderWithProviders(
      <BlockEditor
        label="Содержание страницы"
        value={{
          schemaVersion: 1,
          blocks: [
            { id: 'h', type: 'heading', level: 2, text: [{ text: 'Заголовок' }] },
            { id: 'p', type: 'paragraph', text: [{ text: 'Текст', marks: ['bold'] }] },
            { id: 'img', type: 'image', fileId: 'f1', alt: '' },
          ],
        }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByRole('group', { name: 'Содержание страницы' })).toBeInTheDocument();
    expect(screen.getByRole('toolbar', { name: 'Форматирование' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Добавьте описание изображения');
    await expectAccessible(container);
  });

  it('create course dialog (UX-01: only the title is required) is an accessible modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateCourseDialog />);
    await user.click(screen.getByRole('button', { name: 'Создать курс' }));
    const dialog = await screen.findByRole('dialog', { name: 'Новый курс' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByLabelText(/Название/)).toHaveFocus();
    expect(screen.queryByLabelText('Краткое имя')).not.toBeInTheDocument();
    await expectAccessible(document.body);
  });

  it('shortcuts help dialog (UX-11)', async () => {
    renderWithProviders(<ShortcutsHelp open onOpenChange={() => undefined} />);
    expect(await screen.findByRole('dialog', { name: 'Горячие клавиши' })).toBeInTheDocument();
    await expectAccessible(document.body);
  });
});
