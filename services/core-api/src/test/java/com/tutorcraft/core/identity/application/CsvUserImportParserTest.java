package com.tutorcraft.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.identity.domain.ImportRow;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvUserImportParserTest {

    private final CsvUserImportParser parser = new CsvUserImportParser();

    @Test
    void parsesRowsWithBomAndOptionalColumns() {
        String csv = "﻿email,firstName,lastName,courseShortName,role\n"
                + "anna@school.ru, Анна ,Петрова,MATH-7,student\n"
                + "\n"
                + "\"ivan@school.ru\",Иван,\"Иванов, мл.\",,\n";

        List<ImportRow.Raw> rows = parse(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(new ImportRow.Raw(2, "anna@school.ru", "Анна", "Петрова", "MATH-7", "student"));
        assertThat(rows.get(1).lastName()).isEqualTo("Иванов, мл.");
        assertThat(rows.get(1).courseShortName()).isEmpty();
    }

    @Test
    void optionalColumnsMayBeAbsentAndHeaderCaseIsIgnored() {
        List<ImportRow.Raw> rows = parse("Email,FirstName,LastName\na@b.ru,A,B\n");

        assertThat(rows).containsExactly(new ImportRow.Raw(2, "a@b.ru", "A", "B", null, null));
    }

    @Test
    void rejectsMissingRequiredColumns() {
        assertThatThrownBy(() -> parse("email,name\na@b.ru,A\n"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(IdentityErrors.IMPORT_BAD_HEADER);
    }

    @Test
    void rejectsTooLargeFileBeforeReading() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0]), CsvUserImportParser.MAX_BYTES + 1))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(IdentityErrors.IMPORT_FILE_TOO_LARGE);
    }

    @Test
    void rejectsTooManyRows() {
        StringBuilder csv = new StringBuilder("email,firstName,lastName\n");
        for (int i = 0; i <= CsvUserImportParser.MAX_ROWS; i++) {
            csv.append("u").append(i).append("@b.ru,A,B\n");
        }

        assertThatThrownBy(() -> parse(csv.toString()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(IdentityErrors.IMPORT_TOO_MANY_ROWS);
    }

    private List<ImportRow.Raw> parse(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return parser.parse(new ByteArrayInputStream(bytes), bytes.length);
    }
}
