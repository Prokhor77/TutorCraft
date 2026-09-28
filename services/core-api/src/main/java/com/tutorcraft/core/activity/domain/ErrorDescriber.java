package com.tutorcraft.core.activity.domain;

/**
 * Описание непредвиденной ошибки сервера для журнала: тип, замаскированное сообщение и сокращённый стек
 * (причины включены, кадры фреймворков свёрнуты), чтобы найти место падения без доступа к логам контейнера.
 */
public final class ErrorDescriber {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_CAUSES = 5;
    private static final int MAX_FRAMES_PER_CAUSE = 25;
    private static final String APP_PACKAGE = "com.tutorcraft.";
    private static final String PROXY_MARKER = "$$";

    private ErrorDescriber() {
    }

    public static ActivityEntry.ErrorDetails describe(String code, Throwable error, int maxStackLength) {
        if (error == null) {
            return new ActivityEntry.ErrorDetails(code, null, null, null);
        }
        Throwable root = rootCause(error);
        String message = SensitiveDataMasker.maskAndTruncate(root.getMessage(), MAX_MESSAGE_LENGTH);
        String stack = SensitiveDataMasker.maskAndTruncate(stackOf(error), maxStackLength);
        return new ActivityEntry.ErrorDetails(code, root.getClass().getName(), message, stack);
    }

    static Throwable rootCause(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < MAX_CAUSES && current.getCause() != null && current.getCause() != current; depth++) {
            current = current.getCause();
        }
        return current;
    }

    static String stackOf(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; depth < MAX_CAUSES && current != null; depth++) {
            if (depth > 0) {
                out.append("Caused by: ");
            }
            out.append(current.getClass().getName()).append('\n');
            appendFrames(out, current.getStackTrace());
            current = current.getCause() == current ? null : current.getCause();
        }
        return out.toString();
    }

    /** Первый кадр и кадры приложения выводятся, подряд идущие кадры библиотек сворачиваются в «... N more». */
    private static void appendFrames(StringBuilder out, StackTraceElement[] frames) {
        int shown = 0;
        int collapsed = 0;
        for (int index = 0; index < frames.length; index++) {
            boolean keep = index == 0 || isApplicationFrame(frames[index]);
            if (!keep || shown >= MAX_FRAMES_PER_CAUSE) {
                collapsed += 1;
                continue;
            }
            collapsed = flushSkipped(out, collapsed);
            out.append("  at ").append(frames[index]).append('\n');
            shown += 1;
        }
        flushSkipped(out, collapsed);
    }

    /** Кадры прокси Spring ({@code $$SpringCGLIB$$}) не несут информации — только код приложения. */
    private static boolean isApplicationFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.startsWith(APP_PACKAGE) && !className.contains(PROXY_MARKER);
    }

    private static int flushSkipped(StringBuilder out, int skipped) {
        if (skipped > 0) {
            out.append("  ... ").append(skipped).append(" more\n");
        }
        return 0;
    }
}
