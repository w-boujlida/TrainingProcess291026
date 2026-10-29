package com.adient.training.service;

import com.adient.training.exception.ApiException;
import com.adient.training.service.UserHrProfileService.Field;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class HrWorkbookReader {
    public static final List<String> COLUMNS = List.of(
            "A B C D F G H I J K L N O P Q R S T U V W X Y Z AA AB AC AD AE AF AG AH AK AL AM AN AO AP AQ AR AS AT AU BC BD BE BF BG BH BI BJ BK BL BM BR BV BW BX BZ CA CB CC".split(" "));
    static final List<String> LABELS = List.of(
            "Actif/Inactif", "Matricule", "Civilité", "Nom et prénom", "Département", "Poste Occupé",
            "LIGNE DE PRODUCTION", "superviseur", "Poste", "TYPE DE TRAVAIL", "Catégorie SAGE FC",
            "Date d'embauche", "Date pour titulariser", "Nbre de Jrs restant/ECHEANCE", "Date de départ",
            "MOTIFS DEPART", "Type depart", "Type de contrat", "Statut", "Date début contrat CDD",
            "Date Fin contrat CDI", "Date début contrat CAIP", "Date Fin contrat CAIP", "1er Contrat SAGE TUNISIA",
            "MOIS d'embauche", "Année d'embauche", "Année Titularisation", "Ancienneté", "Tranche d'Ancienneté",
            "DATE DE NAISSANCE", "AGE", "Tranche d'age", "CIN", "Date Délivrance CIN", "HOMME / FEMME",
            "Situation Familial", "N°enfant", "Niveau scolaire", "Catégorie", "Echelon",
            "Prochaine Date d'echelon", "Type de Salaire", "COUT/H", "ADRESSE", "COMMUNE", "CODE POSTALE",
            "Visite Médicale", "N° TELEPHONE", "N° TELEPHONE CONTACT FAM", "Bien da la Société", "DESTINATION",
            "Département Bulletin", "Level", "MONTANT", "bus", "Formation", "LIGNE", "LIGNE",
            "Date de promotion", "Date de validation de promotion", "statut personnel", "Formation interne");

    public record Employee(int row, String username, String fullName, Boolean active, String workType, List<Field> fields) {}
    public record Issue(int row, String message) {}
    public record WorkbookData(byte[] bytes, String filename, List<Employee> employees, List<Issue> errors,
                               int excelErrors, int uncalculated) {}

    public WorkbookData read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("Fichier Excel vide.");
        if (file.getSize() > 10 * 1024 * 1024) throw bad("Fichier trop volumineux (10 Mo maximum).");
        String filename = Objects.toString(file.getOriginalFilename(), "").replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || filename.length() > 255)
            throw bad("Format non supporté : utilisez un fichier Excel .xlsx.");
        try {
            byte[] bytes = file.getBytes();
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(boundedArchive(bytes)))) {
                Sheet sheet = workbook.getSheet("RH");
                if (sheet == null || sheet.getRow(0) == null) throw bad("La feuille RH et ses en-têtes sont obligatoires.");
                if (sheet.getLastRowNum() > 10000) throw bad("Maximum 10000 lignes par fichier RH.");
                for (int i = 0; i < COLUMNS.size(); i++) {
                    if (!normalize(value(sheet.getRow(0).getCell(column(COLUMNS.get(i))), workbook.isDate1904()))
                            .equals(normalize(LABELS.get(i))))
                        throw bad("En-tête RH incorrect en colonne " + COLUMNS.get(i) + ".");
                }
                List<Employee> employees = new ArrayList<>();
                List<Issue> errors = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                int excelErrors = 0, uncalculated = 0;
                for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                    Row row = sheet.getRow(index);
                    if (row == null) continue;
                    String username = value(row.getCell(1), workbook.isDate1904()).trim();
                    String name = value(row.getCell(3), workbook.isDate1904()).trim();
                    if (username.isEmpty() && name.isEmpty()) continue;
                    try {
                        if (!username.matches("[A-Za-z0-9._-]{1,50}")) throw bad("Matricule manquant ou invalide.");
                        if (!seen.add(username.toLowerCase(Locale.ROOT))) throw bad("Matricule en double dans le fichier.");
                        if (name.length() > 120 || invalidIdentityCell(row.getCell(1)) || invalidIdentityCell(row.getCell(3)))
                            throw bad("Nom ou matricule invalide.");
                        String activeText = normalize(value(row.getCell(0), workbook.isDate1904()));
                        Boolean active = switch (activeText) {
                            case "" -> null;
                            case "si", "actif" -> true;
                            case "non-actif", "inactif" -> false;
                            default -> throw bad("Statut actif/inactif invalide.");
                        };
                        String typeText = normalize(value(row.getCell(10), workbook.isDate1904()));
                        String type = switch (typeText) {
                            case "" -> null;
                            case "cadre" -> "CADRE";
                            case "direct", "directs" -> "DIRECT";
                            case "indirect", "indirects" -> "INDIRECT";
                            default -> throw bad("Type de travail invalide.");
                        };
                        List<Field> fields = new ArrayList<>();
                        for (int i = 0; i < COLUMNS.size(); i++) {
                            Cell cell = row.getCell(column(COLUMNS.get(i)));
                            String text = value(cell, workbook.isDate1904());
                            if (text.length() > 10000) throw bad("Une cellule RH dépasse 10000 caractères.");
                            if (cell != null && effectiveType(cell) == CellType.ERROR) excelErrors++;
                            if (text.equals("Non calculé dans Excel")) uncalculated++;
                            fields.add(new Field(COLUMNS.get(i), LABELS.get(i), text));
                        }
                        employees.add(new Employee(index + 1, username, name, active, type, fields));
                    } catch (ApiException e) {
                        errors.add(new Issue(index + 1, e.getMessage()));
                    }
                }
                if (employees.isEmpty() && errors.isEmpty()) throw bad("Aucun salarié dans la feuille RH.");
                return new WorkbookData(bytes, filename, employees, errors, excelErrors, uncalculated);
            }
        } catch (IOException | EncryptedDocumentException | POIXMLException | IllegalArgumentException e) {
            ApiException failure = bad("Impossible de lire le fichier Excel : utilisez un modèle .xlsx valide, non chiffré.");
            failure.initCause(e);
            throw failure;
        }
    }

    private static byte[] boundedArchive(byte[] bytes) throws IOException {
        // HR exports contain long repeated formatting runs. Repack with no compression after
        // enforcing expansion limits, avoiding POI's transient-ratio false positives.
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes));
             var output = new ByteArrayOutputStream();
             var archive = new ZipOutputStream(output)) {
            archive.setLevel(0);
            byte[] buffer = new byte[8192];
            long total = 0;
            int entries = 0;
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > 2000) throw bad("Le classeur RH dépasse les limites de décompression autorisées.");
                archive.putNextEntry(new ZipEntry(entry.getName()));
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    total += count;
                    if (size > 64L * 1024 * 1024 || total > 128L * 1024 * 1024)
                        throw bad("Le classeur RH dépasse les limites de décompression autorisées.");
                    archive.write(buffer, 0, count);
                }
                archive.closeEntry();
                input.closeEntry();
            }
            if (entries == 0) throw new IOException("Empty Excel archive");
            archive.finish();
            return output.toByteArray();
        }
    }

    private static int column(String letter) { return CellReference.convertColStringToIndex(letter); }
    private static CellType effectiveType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }
    private static boolean invalidIdentityCell(Cell cell) {
        return cell != null && (effectiveType(cell) == CellType.ERROR
                || value(cell, false).equals("Non calculé dans Excel"));
    }
    static String value(Cell cell, boolean date1904) {
        if (cell == null) return "";
        CellType type = effectiveType(cell);
        if (cell instanceof XSSFCell xml && cell.getCellType() == CellType.FORMULA
                && (!xml.getCTCell().isSetV() || (type == CellType.NUMERIC && xml.getCTCell().getV().isBlank()))) {
            return "Non calculé dans Excel";
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case ERROR -> FormulaError.forInt(cell.getErrorCellValue()).getString();
            case NUMERIC -> {
                double number = cell.getNumericCellValue();
                if (!Double.isFinite(number)) throw bad("Valeur numérique Excel invalide.");
                var style = cell.getCellStyle();
                if (DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString())) {
                    if (number < 0 || number >= (date1904 ? 2957004 : 2958466)) yield "#VALUE!";
                    var date = DateUtil.getLocalDateTime(number, date1904);
                    yield date.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                            ? date.toLocalDate().toString() : date.toString();
                }
                yield BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
            }
            default -> "";
        };
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('\u00a0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
