package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.gradebook.application.GradebookExportWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

/** CSV UTF-8 с BOM (корректно открывается в Excel), разделитель «;» — привычный для ru-локали Excel. */
@Component
class CsvGradebookWriter implements GradebookExportWriter {

    private static final String FORMAT = "csv";
    private static final String CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final char BOM = '﻿';
    private static final char DELIMITER = ';';
    private static final String FORMULA_PREFIXES = "=+-@";
    private static final String TEXT_GUARD = "'";

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public void write(ExportTable table, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(BOM);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(DELIMITER).build();
        CSVPrinter printer = new CSVPrinter(writer, format);
        printer.printRecord(table.headers().stream().map(CsvGradebookWriter::safeText).toList());
        for (List<Object> row : table.rows()) {
            printer.printRecord(row.stream().map(CsvGradebookWriter::cell).toList());
        }
        printer.flush();
    }

    private static String cell(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal number -> number.stripTrailingZeros().toPlainString();
            default -> safeText(value.toString());
        };
    }

    /** Защита от CSV-инъекции формул (имена студентов и элементов — пользовательский ввод). */
    private static String safeText(String text) {
        if (text.isEmpty() || FORMULA_PREFIXES.indexOf(text.charAt(0)) < 0) {
            return text;
        }
        return TEXT_GUARD + text;
    }
}
