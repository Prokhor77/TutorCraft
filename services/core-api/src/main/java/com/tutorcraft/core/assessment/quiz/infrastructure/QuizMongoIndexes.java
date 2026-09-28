package com.tutorcraft.core.assessment.quiz.infrastructure;

import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.CATEGORY_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.COURSE_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.QUESTION_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TAGS;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TENANT_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.UPDATED_AT;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.VERSION;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** Индексы банка вопросов и составов тестов (создаются при старте, идемпотентно). */
@Component
class QuizMongoIndexes {

    private final MongoTemplate mongo;

    QuizMongoIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        mongo.indexOps(MongoQuestionRepository.QUESTIONS).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(COURSE_ID, Direction.ASC).on(UPDATED_AT, Direction.DESC).named("questions_course_updated"));
        mongo.indexOps(MongoQuestionRepository.QUESTIONS).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(COURSE_ID, Direction.ASC).on(CATEGORY_ID, Direction.ASC).named("questions_course_category"));
        mongo.indexOps(MongoQuestionRepository.QUESTIONS).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(COURSE_ID, Direction.ASC).on(TAGS, Direction.ASC).named("questions_course_tags"));
        mongo.indexOps(MongoQuestionRepository.VERSIONS).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(QUESTION_ID, Direction.ASC).on(VERSION, Direction.DESC).unique().named("question_versions_question"));
        mongo.indexOps(MongoQuestionCategoryRepository.COLLECTION).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(COURSE_ID, Direction.ASC).named("question_categories_course"));
        mongo.indexOps(MongoQuizLayoutRepository.COLLECTION).ensureIndex(new Index().on(TENANT_ID, Direction.ASC)
                .on(MongoQuizLayoutRepository.SLOTS + "." + QUESTION_ID, Direction.ASC).named("quiz_layouts_questions"));
    }
}
