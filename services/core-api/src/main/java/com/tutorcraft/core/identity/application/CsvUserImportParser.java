package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.ImportRow;
import com.tutorcraft.core.identity.domain.ImportRowValidator;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Разбор CSV импорта пользователей: заголовок {@code email,firstName,lastName,courseShortName,role}
 * (последние два — необязательные), UTF-8 с BOM или без. Лимиты: {@value #MAX_BYTES} байт, {@value #MAX_ROWS} строк.
 */
@Component
public class CsvUserImportParser {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 5000;
    private static final List<String> REQUIRED_COLUMNS = List.of(
            ImportRowValidator.FIELD_EMAIL, ImportRowValidator.FIELD_FIRST_NAME, ImportRowValidator.FIELD_LAST_NAME);
    private static final int HEADER_ROWS = 1;
    private static final char BYTE_ORDER_MARK = '﻿';
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    public List<ImportRow.Raw> parse(InputStream input, long sizeBytes) {
        if (sizeBytes > MAX_BYTES) {
            throw new BusinessRuleException(IdentityErrors.IMPORT_FILE_TOO_LARGE, "File is larger than 5 MB");
        }
        try (Reader reader = withoutBom(input); CSVParser parser = FORMAT.parse(reader)) {
            requireHeader(parser.getHeaderMap());
            return readRows(parser);
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw new BusinessRuleException(IdentityErrors.IMPORT_UNREADABLE, "CSV file cannot be read");
        }
    }

    private static List<ImportRow.Raw> readRows(CSVParser parser) {
        List<ImportRow.Raw> rows = new ArrayList<>();
        for (CSVRecord record : parser) {
            if (rows.size() >= MAX_ROWS) {
                throw new BusinessRuleException(IdentityErrors.IMPORT_TOO_MANY_ROWS, "Too many rows");
            }
            int rowNumber = rows.size() + HEADER_ROWS + 1;
            rows.add(new ImportRow.Raw(rowNumber, value(record, ImportRowValidator.FIELD_EMAIL),
                    value(record, ImportRowValidator.FIELD_FIRST_NAME), value(record, ImportRowValidator.FIELD_LAST_NAME),
                    value(record, ImportRowValidator.FIELD_COURSE), value(record, ImportRowValidator.FIELD_ROLE)));
        }
        return rows;
    }

    private static void requireHeader(Map<String, Integer> header) {
        if (header == null || !REQUIRED_COLUMNS.stream().allMatch(header::containsKey)) {
            throw new BusinessRuleException(IdentityErrors.IMPORT_BAD_HEADER, "Header must be: email,firstName,lastName");
        }
    }

    private static String value(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    private static Reader withoutBom(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        reader.mark(1);
        int first = reader.read();
        if (first != BYTE_ORDER_MARK) {
            reader.reset();
        }
        return reader;
    }
}
