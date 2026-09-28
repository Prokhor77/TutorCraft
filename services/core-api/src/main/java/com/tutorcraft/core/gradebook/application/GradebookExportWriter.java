package com.tutorcraft.core.gradebook.application;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Порт форматирования выгрузки журнала (FR-GRADE-07). Реализации — CSV и XLSX в infrastructure. */
public interface GradebookExportWriter {

    String format();

    String contentType();

    void write(ExportTable table, OutputStream out) throws IOException;

    /** Ячейки строк — String, BigDecimal или null. */
    record ExportTable(String sheetName, List<String> headers, List<List<Object>> rows) {
    }
}
