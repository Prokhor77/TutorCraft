package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.gradebook.application.GradebookExportWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/** XLSX потоково (SXSSF): в памяти держится только окно строк. */
@Component
class XlsxGradebookWriter implements GradebookExportWriter {

    private static final String FORMAT = "xlsx";
    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int ROW_WINDOW = 100;

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
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        try {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(table.sheetName()));
            writeRow(sheet.createRow(0), List.copyOf(table.headers()));
            for (int index = 0; index < table.rows().size(); index++) {
                writeRow(sheet.createRow(index + 1), table.rows().get(index));
            }
            workbook.write(out);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private static void writeRow(Row row, List<?> values) {
        for (int column = 0; column < values.size(); column++) {
            Cell cell = row.createCell(column);
            Object value = values.get(column);
            if (value instanceof BigDecimal number) {
                cell.setCellValue(number.doubleValue());
            } else if (value != null) {
                cell.setCellValue(value.toString());
            }
        }
    }
}
