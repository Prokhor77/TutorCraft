package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.AttemptLayoutBuilder;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.LayoutSlot;
import com.tutorcraft.core.assessment.quiz.domain.QuestionCandidate;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.QuizLayout;
import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Подбор текущих версий вопросов для новой попытки (фиксированные + случайные слоты). */
@Component
public class QuestionDrawer {

    private final QuestionRepository questions;

    public QuestionDrawer(QuestionRepository questions) {
        this.questions = questions;
    }

    public List<AttemptSlot> draw(UUID tenantId, UUID courseId, UUID attemptId, QuizLayout layout, QuizSettings settings) {
        Map<UUID, StoredQuestion> fixedHeads = new HashMap<>(questions.findAll(tenantId, layout.fixedQuestionIds()));
        fixedHeads.values().removeIf(question -> !question.courseId().equals(courseId));
        Map<Integer, List<StoredQuestion>> poolHeads = randomPools(tenantId, courseId, layout);
        Set<UUID> versionIds = new HashSet<>();
        fixedHeads.values().forEach(question -> versionIds.add(question.currentVersionId()));
        poolHeads.values().forEach(pool -> pool.forEach(question -> versionIds.add(question.currentVersionId())));
        Map<UUID, QuestionVersion> versions = questions.findVersions(tenantId, versionIds);
        Map<UUID, QuestionCandidate> fixed = new HashMap<>();
        fixedHeads.forEach((id, head) -> candidate(head, versions).ifPresent(c -> fixed.put(id, c)));
        Map<Integer, List<QuestionCandidate>> pools = new HashMap<>();
        poolHeads.forEach((index, heads) -> pools.put(index,
                heads.stream().map(head -> candidate(head, versions)).flatMap(Optional::stream).toList()));
        return AttemptLayoutBuilder.build(attemptId, layout, fixed, pools, settings);
    }

    private Map<Integer, List<StoredQuestion>> randomPools(UUID tenantId, UUID courseId, QuizLayout layout) {
        Map<Integer, List<StoredQuestion>> pools = new HashMap<>();
        for (int index = 0; index < layout.slots().size(); index++) {
            if (layout.slots().get(index) instanceof LayoutSlot.Random random) {
                pools.put(index, questions.findForRandomSlot(tenantId, courseId, random.categoryId(), random.tag()));
            }
        }
        return pools;
    }

    private static Optional<QuestionCandidate> candidate(StoredQuestion head, Map<UUID, QuestionVersion> versions) {
        return Optional.ofNullable(versions.get(head.currentVersionId())).map(QuestionVersion::asCandidate);
    }
}
