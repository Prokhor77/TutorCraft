package com.tutorcraft.core.seed;

import com.tutorcraft.core.assessment.quiz.domain.QuestionDraft;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Тексты и вопросы демо-курса (блочные документы — контракт §5, схема версии 1). */
final class DemoContent {

    static final String COURSE_TITLE = "Демо-курс: основы математики";
    static final String COURSE_SHORT_NAME = "DEMO-MATH";
    static final String MODULE_INTRO = "Модуль 1. Введение";
    static final String MODULE_PRACTICE = "Модуль 2. Практика";
    static final String PAGE_TITLE = "Добро пожаловать";
    static final String ASSIGNMENT_TITLE = "Домашнее задание: решите 5 задач";
    static final String QUIZ_TITLE = "Проверочный тест";
    static final String FORUM_TITLE = "Вопросы и обсуждения";

    private static final int SCHEMA_VERSION = 1;
    private static final BigDecimal QUESTION_SCORE = BigDecimal.ONE;
    private static final double PI_APPROXIMATION = 3.14;
    private static final double PI_TOLERANCE = 0.01;

    private DemoContent() {
    }

    static Map<String, Object> doc(String... paragraphs) {
        List<Map<String, Object>> blocks = new java.util.ArrayList<>();
        for (int i = 0; i < paragraphs.length; i++) {
            blocks.add(Map.of("id", "p" + (i + 1), "type", "paragraph", "text", List.of(Map.of("text", paragraphs[i]))));
        }
        return Map.of("schemaVersion", SCHEMA_VERSION, "blocks", blocks);
    }

    static Map<String, Object> welcomePage() {
        return doc("Это демонстрационный курс TutorCraft.",
                "Изучите материалы первого модуля, затем выполните задание, пройдите тест и задайте вопросы на форуме.");
    }

    static List<QuestionDraft> quizQuestions() {
        return List.of(
                question("single_choice", "Сумма", "Сколько будет 2 + 2?", Map.of("options", List.of(
                        option("a", "3", false), option("b", "4", true), option("c", "5", false)))),
                question("multiple_choice", "Простые числа", "Выберите простые числа.", Map.of("options", List.of(
                        option("a", "2", true), option("b", "4", false), option("c", "7", true), option("d", "9", false)))),
                question("true_false", "Чётность", "Число 10 чётное.", Map.of("correct", true)),
                question("short_answer", "Столица", "Столица России?", Map.of("answers", List.of(Map.of("pattern", "Москва")))),
                question("numerical", "Число π", "Приближённое значение числа π с точностью до сотых?",
                        Map.of("answers", List.of(Map.of("value", PI_APPROXIMATION, "tolerance", PI_TOLERANCE)))));
    }

    private static QuestionDraft question(String type, String title, String text, Map<String, Object> data) {
        return QuestionDraft.of(type, title, doc(text), QUESTION_SCORE, null, List.of("demo"), data, null);
    }

    private static Map<String, Object> option(String id, String text, boolean correct) {
        return Map.of("id", id, "text", text, "correct", correct);
    }
}
