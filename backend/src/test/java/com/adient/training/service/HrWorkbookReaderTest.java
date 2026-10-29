package com.adient.training.service;

import com.adient.training.exception.ApiException;
import com.adient.training.service.UserHrProfileService.Field;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class HrWorkbookReaderTest {
    static MockMultipartFile workbook(Consumer<Sheet> fill) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("RH");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HrWorkbookReader.COLUMNS.size(); i++)
                header.createCell(CellReference.convertColStringToIndex(HrWorkbookReader.COLUMNS.get(i)))
                        .setCellValue(HrWorkbookReader.LABELS.get(i));
            fill.accept(sheet);
            book.write(out);
            return new MockMultipartFile("file", "RH_test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
    static Row employee(Sheet sheet, int index, String username, String name) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue("si");
        row.createCell(1).setCellValue(username);
        row.createCell(3).setCellValue(name);
        row.createCell(10).setCellValue("DIRECTS");
        return row;
    }
    @Test void parsesExactlySelectedColumnsAndPreservesLeadingZeros() throws Exception {
        var parsed = new HrWorkbookReader().read(workbook(sheet -> {
            var row = employee(sheet, 1, "0012", "Test");
            row.createCell(4).setCellValue("EXCLUDED");
            row.createCell(62).setCellValue("PRIVATE");
        }));
        assertThat(parsed.employees()).hasSize(1);
        assertThat(parsed.employees().getFirst().username()).isEqualTo("0012");
        assertThat(parsed.employees().getFirst().workType()).isEqualTo("DIRECT");
        assertThat(parsed.employees().getFirst().fields()).hasSize(62).noneMatch(f -> f.value().equals("EXCLUDED"));
        assertThat(parsed.errors()).isEmpty();
    }
    @Test void duplicateIdentifiersAndUnknownStatusBlockConfirmation() throws Exception {
        var parsed = new HrWorkbookReader().read(workbook(sheet -> {
            employee(sheet, 1, "12", "Test");
            employee(sheet, 2, "12", "Test");
            employee(sheet, 3, "13", "Test").getCell(0).setCellValue("unknown");
        }));
        assertThat(parsed.errors()).hasSize(2);
    }
    @Test void cachedFormulaIsReadWithoutEvaluationAndMissingValueIsExplicit() throws Exception {
        var parsed = new HrWorkbookReader().read(workbook(sheet -> {
            Row row = employee(sheet, 1, "12", "Test");
            var missing = row.createCell(14);
            missing.setCellFormula("1+2");
            var cached = row.createCell(15);
            cached.setCellFormula("1+2");
            cached.setCellValue(3);
            row.createCell(69).setCellErrorValue(FormulaError.NA.getCode());
        }));
        assertThat(parsed.uncalculated()).isEqualTo(1);
        assertThat(parsed.excelErrors()).isEqualTo(1);
        assertThat(parsed.employees().getFirst().fields())
                .anyMatch(f -> f.column().equals("O") && f.value().equals("Non calculé dans Excel"))
                .anyMatch(f -> f.column().equals("P") && f.value().equals("3"))
                .anyMatch(f -> f.column().equals("BR") && f.value().equals("#N/A"));
    }
    @Test void changedHeaderFailsInsteadOfSilentlyShiftingSensitiveData() throws Exception {
        assertThatThrownBy(() -> new HrWorkbookReader().read(workbook(sheet -> sheet.getRow(0).getCell(1).setCellValue("CIN"))))
                .isInstanceOf(ApiException.class);
    }
    @Test void permitsMoreThan5000RowsButRejectsMoreThan10000() throws Exception {
        var parsed = new HrWorkbookReader().read(workbook(sheet -> {
            for (int i = 1; i <= 5001; i++) employee(sheet, i, Integer.toString(i), "Synthetic");
        }));
        assertThat(parsed.employees()).hasSize(5001);
        assertThatThrownBy(() -> new HrWorkbookReader().read(workbook(sheet -> employee(sheet, 10001, "1", "Synthetic"))))
                .isInstanceOf(ApiException.class);
    }
    @Test void datesSupportBothExcelCalendarsAndInvalidSerialsAreExplicit() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var row = book.createSheet().createRow(0);
            var cell = row.createCell(0);
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
            cell.setCellValue(1);
            assertThat(HrWorkbookReader.value(cell, false)).isEqualTo("1900-01-01");
            assertThat(HrWorkbookReader.value(cell, true)).isEqualTo("1904-01-02");
            cell.setCellValue(-1);
            assertThat(HrWorkbookReader.value(cell, false)).isEqualTo("#VALUE!");
        }
    }
    @Test void rejectsUnreadableFilesWithoutInternalError() {
        assertThatThrownBy(() -> new HrWorkbookReader().read(new MockMultipartFile("file", "bad.xlsx", null, new byte[]{1, 2, 3})))
                .isInstanceOf(ApiException.class);
    }
    @Test void rejectsOversizedExpandedEntriesEvenWhenTheUploadIsSmall() throws Exception {
        byte[] bytes;
        try (var out = new ByteArrayOutputStream(); var zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("large.xml"));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 65; i++) zip.write(chunk);
            zip.closeEntry();
            zip.finish();
            bytes = out.toByteArray();
        }
        assertThat(bytes.length).isLessThan(10 * 1024 * 1024);
        assertThatThrownBy(() -> new HrWorkbookReader().read(new MockMultipartFile("file", "large.xlsx", null, bytes)))
                .isInstanceOf(ApiException.class).hasMessage("Le classeur RH dépasse les limites de décompression autorisées.");
    }
    @Test void blankFieldsMergeByColumnNotByDisplayLabel() {
        var old = List.of(new Field("BW", "LIGNE", "Old A"), new Field("BX", "LIGNE", "Old B"));
        var merged = UserHrImportService.merge(old, List.of(new Field("BW", "LIGNE", ""), new Field("BX", "LIGNE", "New B")));
        assertThat(merged).extracting(Field::value).containsExactly("Old A", "New B");
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "HR_WORKBOOK_PATH", matches = ".+")
    void realWorkbookReadOnlyHasExpectedEmployeePopulation() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(System.getenv("HR_WORKBOOK_PATH")));
        var parsed = new HrWorkbookReader().read(new MockMultipartFile("file", "RH_octobre.xlsx", null, bytes));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.employees()).hasSize(5055).allMatch(e -> e.fields().size() == 62);
        assertThat(parsed.employees().stream().filter(e -> Boolean.TRUE.equals(e.active()))).hasSize(2221);
    }
}
