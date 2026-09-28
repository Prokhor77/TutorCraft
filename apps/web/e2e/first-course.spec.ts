import { expect, test, type Browser, type Page } from '@playwright/test';
import { expectSaved, linkFromMail, PASSWORD, quickCreateItem, t, uniqueEmail } from './support';

/**
 * SPEC §10 «Первый курс за 15 минут» (e2e + usability criterion):
 * создать курс → 2 модуля → страница с текстом и видео → задание со сроком → тест из 5 вопросов →
 * пригласить студента по ссылке → студент проходит → преподаватель проверяет → студент видит оценку.
 */
const COURSE = `Алгебра ${Date.now()}`;
const MODULE_1 = 'Модуль 1';
const MODULE_2 = 'Модуль 2';
const PAGE_TITLE = 'Введение';
const ASSIGNMENT_TITLE = 'Домашнее задание 1';
const QUIZ_TITLE = 'Проверочный тест';
const QUESTION_COUNT = 5;
const GRADE = '90';
const VIDEO_EMBED = 'https://www.youtube.com/embed/dQw4w9WgXcQ';

const teacherEmail = uniqueEmail('teacher');
const studentEmail = uniqueEmail('student');
let courseUrl = '';
let inviteUrl = '';

test.describe.configure({ mode: 'serial' });

async function login(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel(t.auth.email).fill(email);
  await page.getByLabel(t.auth.password).fill(PASSWORD);
  await page.getByRole('button', { name: t.auth.login, exact: true }).click();
  await expect(page).toHaveURL(/\/(home|courses)/);
}

async function newPage(browser: Browser): Promise<Page> {
  return (await browser.newContext()).newPage();
}

test('teacher builds the course', async ({ page }) => {
  const startedAt = Date.now();

  await test.step('register as a tutor', async () => {
    await page.goto('/register');
    await page.getByLabel(t.auth.firstName).fill('Анна');
    await page.getByLabel(t.auth.lastName).fill('Смирнова');
    await page.getByLabel(t.auth.email).fill(teacherEmail);
    await page.getByLabel(t.auth.password).fill(PASSWORD);
    await page.getByRole('button', { name: t.auth.register }).click();
    await expect(page).toHaveURL(/\/courses/);
  });

  await test.step('create a course with only a title (UX-02)', async () => {
    await page.getByRole('button', { name: t.courses.create }).first().click();
    await page.getByLabel(t.courses.title).fill(COURSE);
    await page.getByRole('button', { name: t.courses.createSubmit }).click();
    await expect(page.getByRole('heading', { level: 1 })).toContainText(COURSE);
    courseUrl = page.url();
  });

  await test.step('add two modules', async () => {
    await page.getByRole('button', { name: t.course.createFirstModule }).click();
    await expect(page.getByRole('heading', { name: MODULE_1 })).toBeVisible();
    await page.getByRole('button', { name: t.course.addModule }).click();
    await expect(page.getByRole('heading', { name: MODULE_2 })).toBeVisible();
  });

  await test.step('page with text and video', async () => {
    await quickCreateItem(page, MODULE_1, t.itemTypes.page, PAGE_TITLE);
    await page.getByRole('link', { name: PAGE_TITLE }).click();
    const editor = page.getByRole('group', { name: t.itemView.contentLabel });
    await editor.getByRole('textbox').first().click();
    await page.keyboard.type('Линейное уравнение имеет вид ax + b = 0.');
    await editor
      .getByRole('button', { name: t.editor.insertBlockAfter.replace('{index}', '1') })
      .click();
    await page.getByRole('option', { name: t.editor.kinds.video }).click();
    await page.getByLabel(t.editor.videoUrl).fill(VIDEO_EMBED);
    await expectSaved(page);
    await page.goto(courseUrl);
  });

  await test.step('assignment with a due date (AC-2)', async () => {
    const due = new Date(Date.now() + 3 * 86_400_000).toISOString().slice(0, 16);
    await quickCreateItem(
      page,
      MODULE_1,
      t.itemTypes.assignment,
      ASSIGNMENT_TITLE,
      async (dialog) => {
        await dialog.getByLabel(t.quickCreate.dueAt).fill(due);
        await dialog.getByLabel(t.quickCreate.submissionType).selectOption('both');
      },
    );
  });

  await test.step('question bank: 5 questions', async () => {
    await page.getByRole('link', { name: t.course.tabQuestionBank }).click();
    for (let index = 1; index <= QUESTION_COUNT; index += 1) {
      await page.getByRole('button', { name: t.qbank.newQuestion }).first().click();
      await page.getByLabel(t.qbank.title).fill(`Вопрос ${index}: 2 + ${index} = ?`);
      await page.getByLabel(t.qbank.optionText.replace('{index}', '1')).fill(String(2 + index));
      await page.getByLabel(t.qbank.optionText.replace('{index}', '2')).fill(String(3 + index));
      await page.getByRole('button', { name: t.common.save }).click();
      await expect(page.getByText(`Вопрос ${index}: 2 + ${index} = ?`)).toBeVisible();
    }
  });

  await test.step('quiz from the bank', async () => {
    await page.goto(courseUrl);
    await quickCreateItem(page, MODULE_2, t.itemTypes.quiz, QUIZ_TITLE);
    await page.getByRole('link', { name: QUIZ_TITLE }).click();
    await page.getByRole('tab', { name: t.itemView.tabs.questions }).click();
    await page.getByRole('button', { name: t.quizSlots.addFromBank }).click();
    const dialog = page.getByRole('dialog');
    for (const checkbox of await dialog.getByRole('checkbox').all()) await checkbox.check();
    await dialog
      .getByRole('button', {
        name: t.quizSlots.addSelected.replace('{count}', String(QUESTION_COUNT)),
      })
      .click();
    await page.getByRole('button', { name: t.common.save }).click();
    await expect(page.getByText(t.quizSlots.saved)).toBeVisible();
  });

  await test.step('invite link for students', async () => {
    await page.goto(`${courseUrl}/participants`);
    await page.getByRole('button', { name: t.participants.invite }).first().click();
    await page.getByRole('button', { name: t.participants.createLink }).click();
    inviteUrl = await page.getByLabel(t.participants.inviteUrl).inputValue();
    expect(inviteUrl).toMatch(/\/join\//);
  });

  await test.step('create the student account with an email invitation', async () => {
    await page.goto('/admin/users');
    await page.getByRole('button', { name: t.adminUsers.create }).first().click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(t.auth.email).fill(studentEmail);
    await dialog.getByLabel(t.auth.firstName).fill('Иван');
    await dialog.getByLabel(t.auth.lastName).fill('Петров');
    await dialog.getByRole('button', { name: t.common.create }).click();
    await expect(page.getByText(studentEmail)).toBeVisible();
  });

  // Usability criterion: the whole authoring flow fits into 15 minutes of automation time with slack.
  expect(Date.now() - startedAt).toBeLessThan(15 * 60_000);
});

test('student joins, submits the assignment and takes the quiz', async ({ browser }) => {
  const page = await newPage(browser);

  await test.step('accept the email invitation', async () => {
    const acceptUrl = await linkFromMail(
      studentEmail,
      /https?:\/\/[^\s"'<>]+\/accept-invite\?token=[^\s"'<>]+/,
    );
    await page.goto(acceptUrl);
    await page.getByLabel(t.auth.firstName).fill('Иван');
    await page.getByLabel(t.auth.lastName).fill('Петров');
    await page.getByLabel(t.auth.password).fill(PASSWORD);
    await page.getByRole('button', { name: t.auth.acceptInvite }).click();
    await expect(page).toHaveURL(/\/home/);
  });

  await test.step('join the course by the invite link', async () => {
    await page.goto(new URL(inviteUrl).pathname);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(COURSE);
  });

  await test.step('«Мои задачи» deep-links to the assignment (AC-9)', async () => {
    await page.goto('/home');
    await page
      .getByRole('link', { name: new RegExp(ASSIGNMENT_TITLE) })
      .first()
      .click();
    await expect(page.getByRole('heading', { name: ASSIGNMENT_TITLE })).toBeVisible();
  });

  await test.step('submit the assignment with an Idempotency-Key', async () => {
    const answer = page.getByRole('group', { name: t.assignment.answer });
    await answer.getByRole('textbox').first().click();
    await page.keyboard.type('x = 2');
    const submitRequest = page.waitForRequest((request) =>
      request.url().includes('/my-submission/submit'),
    );
    await page.getByRole('button', { name: t.assignment.submit }).click();
    expect((await submitRequest).headers()['idempotency-key']).toBeTruthy();
    await expect(page.getByText(t.assignment.submitted)).toBeVisible();
  });

  await test.step('take the quiz', async () => {
    await page.goto(courseUrl);
    await page.getByRole('link', { name: QUIZ_TITLE }).click();
    await page.getByRole('button', { name: t.quiz.start }).click();
    for (;;) {
      for (const group of await page.getByRole('group').all()) {
        const radio = group.getByRole('radio').first();
        if (await radio.count()) await radio.check();
      }
      const next = page.getByRole('button', { name: t.quiz.next });
      if (!(await next.isVisible())) break;
      await next.click();
    }
    await page.getByRole('button', { name: t.quiz.finish }).click();
    await page.getByRole('button', { name: t.quiz.finishConfirm }).click();
    await expect(page.getByRole('heading', { name: t.quiz.resultTitle })).toBeVisible();
  });
});

test('teacher grades from the unified inbox with shortcuts (AC-8)', async ({ browser }) => {
  const page = await newPage(browser);
  await login(page, teacherEmail);
  await page.goto('/grading');
  await expect(page.getByText(ASSIGNMENT_TITLE).first()).toBeVisible();
  await page.getByRole('link', { name: t.grading.startReview }).click();
  await page.getByLabel(/Балл/).fill(GRADE);
  await page.keyboard.press('Control+Enter');
  await expect(page.getByText(t.grading.saved)).toBeVisible();
});

test('student sees the grade', async ({ browser }) => {
  const page = await newPage(browser);
  await login(page, studentEmail);
  await page.goto(courseUrl);
  await page.getByRole('link', { name: ASSIGNMENT_TITLE }).click();
  await expect(page.getByText(t.assignment.yourGrade)).toBeVisible();
  await expect(page.getByText(`${GRADE} / 100`)).toBeVisible();
});

test('«Мои задачи» is readable at 360px without horizontal scroll (UX-08, AC-9)', async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await login(page, studentEmail);
  await page.goto('/home');
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});
