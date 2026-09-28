package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Разворачивает состав теста в слоты попытки (FR-QUIZ-02): случайные вопросы тянутся при старте без повторов,
 * при включённом перемешивании вопросы перемешиваются внутри страницы, страницы нумеруются подряд.
 * Детерминирован по id попытки.
 */
public final class AttemptLayoutBuilder {

    private AttemptLayoutBuilder() {
    }

    /**
     * @param fixed       текущие версии вопросов фиксированных слотов (удалённые вопросы отсутствуют — слот пропускается)
     * @param randomPools кандидаты для случайных слотов по индексу слота в составе
     */
    public static List<AttemptSlot> build(UUID attemptId, QuizLayout layout, Map<UUID, QuestionCandidate> fixed,
                                          Map<Integer, List<QuestionCandidate>> randomPools, QuizSettings settings) {
        List<Entry> entries = collect(attemptId, layout, fixed, randomPools, settings.questionsPerPage());
        Map<Integer, List<Entry>> byPage = new TreeMap<>();
        entries.forEach(entry -> byPage.computeIfAbsent(entry.page(), page -> new ArrayList<>()).add(entry));
        List<AttemptSlot> slots = new ArrayList<>();
        int pageNumber = 0;
        for (List<Entry> pageEntries : byPage.values()) {
            pageNumber++;
            List<Entry> ordered = settings.shuffleQuestions()
                    ? SeededShuffle.shuffle(pageEntries, SeededShuffle.seed(attemptId, -pageNumber)) : pageEntries;
            for (Entry entry : ordered) {
                slots.add(toSlot(attemptId, slots.size() + 1, pageNumber, entry, settings.shuffleAnswers()));
            }
        }
        return List.copyOf(slots);
    }

    private static List<Entry> collect(UUID attemptId, QuizLayout layout, Map<UUID, QuestionCandidate> fixed,
                                       Map<Integer, List<QuestionCandidate>> randomPools, int perPage) {
        Set<UUID> used = new HashSet<>(fixed.keySet());
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < layout.slots().size(); index++) {
            LayoutSlot slot = layout.slots().get(index);
            int page = slot.page() == null ? index / perPage + 1 : slot.page();
            for (QuestionCandidate candidate : pick(attemptId, index, slot, fixed, randomPools, used)) {
                entries.add(new Entry(page, slot.points() == null ? candidate.defaultScore() : slot.points(), candidate));
            }
        }
        return entries;
    }

    private static List<QuestionCandidate> pick(UUID attemptId, int index, LayoutSlot slot, Map<UUID, QuestionCandidate> fixed,
                                                Map<Integer, List<QuestionCandidate>> randomPools, Set<UUID> used) {
        return switch (slot) {
            case LayoutSlot.Fixed single -> fixed.containsKey(single.questionId())
                    ? List.of(fixed.get(single.questionId())) : List.of();
            case LayoutSlot.Random random -> draw(randomPools.getOrDefault(index, List.of()), random.count(),
                    SeededShuffle.seed(attemptId, Integer.MIN_VALUE + index), used);
        };
    }

    private static List<QuestionCandidate> draw(List<QuestionCandidate> pool, int count, long seed, Set<UUID> used) {
        List<QuestionCandidate> available = pool.stream().filter(c -> !used.contains(c.questionId()))
                .sorted(Comparator.comparing(QuestionCandidate::questionId)).toList();
        List<QuestionCandidate> drawn = SeededShuffle.shuffle(available, seed).stream().limit(count).toList();
        drawn.forEach(candidate -> used.add(candidate.questionId()));
        return drawn;
    }

    private static AttemptSlot toSlot(UUID attemptId, int number, int page, Entry entry, boolean shuffleAnswers) {
        QuestionCandidate candidate = entry.candidate();
        List<String> order = OptionOrder.forQuestion(candidate.data(), shuffleAnswers, SeededShuffle.seed(attemptId, number));
        return new AttemptSlot(number, page, entry.points(), candidate.questionId(), candidate.versionId(), order);
    }

    private record Entry(int page, BigDecimal points, QuestionCandidate candidate) {
    }
}
