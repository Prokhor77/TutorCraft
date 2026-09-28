package com.tutorcraft.core.assessment.assignment.domain;

/** Коды ошибок модуля заданий (тексты — i18n/assessment*.properties). */
public final class AssignmentErrors {

    public static final String NOT_FOUND = "assignment.not_found";
    public static final String SUBMISSION_NOT_FOUND = "submission.not_found";
    public static final String NOT_OPEN = "assignment.not_open";
    public static final String CLOSED = "assignment.closed";
    public static final String ALREADY_SUBMITTED = "assignment.already_submitted";
    public static final String NOT_EDITABLE = "assignment.not_editable";
    public static final String EMPTY_SUBMISSION = "assignment.empty_submission";
    public static final String OFFLINE = "assignment.offline";
    public static final String ATTEMPTS_EXHAUSTED = "assignment.attempts_exhausted";
    public static final String NOT_LATEST_ATTEMPT = "assignment.not_latest_attempt";

    private AssignmentErrors() {
    }
}
