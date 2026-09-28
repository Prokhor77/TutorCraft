package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.progress.application.CompletionQueryService.ProgressReport;
import com.tutorcraft.core.progress.application.CompletionQueryService.ReportItem;
import com.tutorcraft.core.progress.application.CompletionQueryService.ReportRow;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Экспорт отчёта о прогрессе в CSV (UTF-8 с BOM — корректно открывается в Excel). */
public final class ProgressReportCsv {

    private static final String BOM = "\uFEFF";
    private static final String DONE = "1";
    private static final String NOT_DONE = "0";
    private static final String FORMULA_PREFIXES = "=+-@";
    private static final String FORMULA_ESCAPE = "'";

    private ProgressReportCsv() {
    }

    public static String write(ProgressReport report, List<String> fixedHeaders) {
        List<String> header = new ArrayList<>(fixedHeaders);
        report.items().forEach(item -> header.add(safe(item.title())));
        StringWriter out = new StringWriter();
        out.write(BOM);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).build();
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (ReportRow row : report.rows()) {
                printer.printRecord(record(row, report.items()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("CSV export failed", e);
        }
        return out.toString();
    }

    private static List<Object> record(ReportRow row, List<ReportItem> items) {
        Set<UUID> done = new HashSet<>(row.completed());
        List<Object> values = new ArrayList<>();
        values.add(safe(row.userName()));
        values.add(row.percent());
        values.add(row.completedAt() == null ? "" : row.completedAt().toString());
        items.forEach(item -> values.add(done.contains(item.id()) ? DONE : NOT_DONE));
        return values;
    }

    /** Защита от CSV-инъекции формул в табличных редакторах. */
    static String safe(String value) {
        if (value == null || value.isEmpty() || FORMULA_PREFIXES.indexOf(value.charAt(0)) < 0) {
            return value;
        }
        return FORMULA_ESCAPE + value;
    }
}
