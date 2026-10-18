package com.adient.training.service;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.CandidateRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CandidateExcelService {

    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("d/M/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private static final String[] HEADERS = {
            "Matricule", "Nom et prénom *", "CIN *", "Date de naissance", "Téléphone", "E-mail", "Adresse",
            "Poste / fonction", "Département / ligne", "Date de recrutement", "Niveau d'étude / diplôme",
            "Shift", "Formatrice", "Team leader", "Statut", "Remarques", "Niveau", "Fin période d'essai"};
    private static final int[] WIDTHS = {14, 26, 14, 16, 16, 26, 30, 24, 22, 18, 22, 12, 22, 22, 15, 40, 14, 18};
    /** Columns 16+ (level, trial end) only exist for categories with a training level. */
    private static final int BASE_COLUMNS = 16;
    private static final Set<Integer> DATE_COLS = Set.of(3, 9);

    private final CandidateRepository candidates;
    private final UserRepository users;
    private final CandidateService candidateService;
    private final Validator validator;

    public CandidateExcelService(CandidateRepository candidates, UserRepository users,
                                 CandidateService candidateService, Validator validator) {
        this.candidates = candidates;
        this.users = users;
        this.candidateService = candidateService;
        this.validator = validator;
    }

    // ---------------------------------------------------------------- export

    @Transactional(readOnly = true)
    public byte[] export(RecruitmentCategory category) {
        List<Candidate> list = candidates.findAllByCategoryOrderByCreatedAtDesc(category);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = createDataSheet(wb, category);
            CellStyle dateStyle = dateStyle(wb);
            int r = 1;
            for (Candidate c : list) {
                Row row = sheet.createRow(r++);
                TrainingLevel level = c.getTrainingLevel();
                Object[] values = {c.getMatricule(), c.getFullName(), c.getCin(), c.getBirthDate(), c.getPhone(),
                        c.getEmail(), c.getAddress(), c.getPosition(), c.getDepartment(), c.getHireDate(),
                        c.getEducationLevel(), c.getShift() == null ? null : c.getShift().getLabel(),
                        c.getTrainer() == null ? null : c.getTrainer().getFullName(),
                        c.getTeamLeader() == null ? null : c.getTeamLeader().getFullName(),
                        c.getStatus().getLabel(), c.getRemarks(),
                        level == null ? null : level.getLabel(),
                        level == null ? null : level.trialEnd(c.getHireDate())};
                for (int i = 0; i < columnCount(category); i++) {
                    if (values[i] == null) continue;
                    Cell cell = row.createCell(i);
                    if (values[i] instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateStyle);
                    } else {
                        cell.setCellValue(values[i].toString());
                    }
                }
            }
            addValidations(sheet, category, Math.max(r, 2) + 500);
            return toBytes(wb);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la génération du fichier Excel.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] template(RecruitmentCategory category) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = createDataSheet(wb, category);
            addValidations(sheet, category, 1000);

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Import en masse — " + category.getLabel(),
                    "",
                    "• Remplir une ligne par candidat dans la feuille « " + category.getLabel() + " » à partir de la ligne 2.",
                    "• Colonnes obligatoires : Nom et prénom, CIN" + (category.hasFixedPositions() ? ", Poste." : "."),
                    category.hasFixedPositions()
                            ? "• Poste (liste déroulante) : " + String.join(", ", category.getPositions())
                            : "• Poste : texte libre.",
                    category.requiresTrainingLevel()
                            ? "• Niveau (obligatoire) : " + Arrays.stream(TrainingLevel.values())
                                    .map(l -> l.getLabel() + " (" + l.getTrialLabel() + ")").collect(Collectors.joining(", "))
                                    + ". La fin de période d'essai est calculée à partir de la date de recrutement (colonne ignorée à l'import)."
                                    + " Statut « Validé » : la personne passe automatiquement en Direct ; le Poste doit alors être un poste Direct : " + String.join(", ", RecruitmentCategory.DIRECT.getPositions()) + "."
                            : "",
                    "• Si le CIN existe déjà dans cette catégorie, la fiche est mise à jour (les cellules vides ne modifient rien).",
                    "• Dates : format JJ/MM/AAAA ou cellule de type date.",
                    "• Shift : " + Arrays.stream(WorkShift.values()).map(WorkShift::getLabel).collect(Collectors.joining(", ")),
                    "• Statut : " + Arrays.stream(CandidateStatus.values()).map(CandidateStatus::getLabel).collect(Collectors.joining(", "))
                            + " (vide = Candidat pour un nouveau candidat).",
                    "• Formatrice / Team leader : nom complet ou identifiant d'un compte existant.",
                    "• Les lignes valides sont importées ; les lignes en erreur sont listées avec leur motif.",
                    "• Maximum " + MAX_ROWS + " lignes par fichier.",
            };
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setFontHeightInPoints((short) 13);
            CellStyle title = wb.createCellStyle();
            title.setFont(bold);
            help.getRow(0).getCell(0).setCellStyle(title);
            help.setColumnWidth(0, 120 * 256);
            return toBytes(wb);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la génération du fichier Excel.");
        }
    }

    // ---------------------------------------------------------------- import

    public ImportResult importFile(AuthUser actor, RecruitmentCategory category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucun fichier reçu.");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Format non supporté : utilisez un fichier Excel .xlsx.");
        }

        Map<String, AppUser> trainers = staffIndex(Role.FORMATRICE);
        Map<String, AppUser> leaders = staffIndex(Role.TEAM_LEADER);
        DataFormatter formatter = new DataFormatter(Locale.FRANCE);

        int created = 0, updated = 0, skipped = 0;
        List<ImportError> errors = new ArrayList<>();
        Set<String> seenCins = new HashSet<>();

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Map<Integer, Integer> columns = mapHeader(sheet.getRow(sheet.getFirstRowNum()), formatter);
            int first = sheet.getFirstRowNum() + 1;
            int last = sheet.getLastRowNum();
            if (last - first + 1 > MAX_ROWS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Fichier trop volumineux : " + MAX_ROWS + " lignes maximum.");
            }

            for (int r = first; r <= last; r++) {
                Row row = sheet.getRow(r);
                int line = r + 1;
                if (row == null || isBlank(row, formatter)) { continue; }
                try {
                    RowData d = readRow(row, columns, formatter);
                    if (d.cin != null && !seenCins.add(d.cin.toUpperCase(Locale.ROOT))) {
                        throw new RowException("CIN en double dans le fichier.");
                    }
                    Optional<Candidate> existing = d.cin == null ? Optional.empty() : candidates.findByCinIgnoreCase(d.cin);
                    if (existing.isPresent() && existing.get().getCategory() != category) {
                        throw new RowException("Ce CIN existe déjà dans la catégorie " + existing.get().getCategory().getLabel() + ".");
                    }
                    CandidateRequest req = toRequest(d, category, existing.orElse(null), trainers, leaders);
                    validate(req);
                    if (existing.isPresent()) {
                        candidateService.update(existing.get().getId(), req);
                        updated++;
                    } else {
                        candidateService.create(actor, req);
                        created++;
                    }
                } catch (RowException | ApiException e) {
                    errors.add(new ImportError(line, e.getMessage()));
                    skipped++;
                } catch (RuntimeException e) {
                    errors.add(new ImportError(line, "Ligne invalide."));
                    skipped++;
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier Excel.");
        }
        return new ImportResult(created, updated, skipped, errors);
    }

    private Map<Integer, Integer> mapHeader(Row header, DataFormatter fmt) {
        if (header == null) throw new ApiException(HttpStatus.BAD_REQUEST, "La ligne d'en-tête est manquante.");
        Map<String, Integer> expected = new HashMap<>();
        for (int i = 0; i < HEADERS.length; i++) {
            expected.put(normalize(HEADERS[i]), i);
            // Also accept the short form of the header, e.g. "Poste" for "Poste / fonction".
            int slash = HEADERS[i].indexOf('/');
            if (slash > 0) expected.putIfAbsent(normalize(HEADERS[i].substring(0, slash)), i);
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (Cell cell : header) {
            Integer field = expected.get(normalize(fmt.formatCellValue(cell)));
            if (field != null) map.put(field, cell.getColumnIndex());
        }
        if (!map.containsKey(1) || !map.containsKey(2)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "En-têtes invalides : les colonnes « Nom et prénom » et « CIN » sont obligatoires. Utilisez le modèle.");
        }
        return map;
    }

    private RowData readRow(Row row, Map<Integer, Integer> cols, DataFormatter fmt) {
        RowData d = new RowData();
        d.matricule = text(row, cols.get(0), fmt);
        d.fullName = text(row, cols.get(1), fmt);
        d.cin = text(row, cols.get(2), fmt);
        d.birthDate = date(row, cols.get(3), fmt, "Date de naissance");
        d.phone = text(row, cols.get(4), fmt);
        d.email = text(row, cols.get(5), fmt);
        d.address = text(row, cols.get(6), fmt);
        d.position = text(row, cols.get(7), fmt);
        d.department = text(row, cols.get(8), fmt);
        d.hireDate = date(row, cols.get(9), fmt, "Date de recrutement");
        d.educationLevel = text(row, cols.get(10), fmt);
        d.shift = text(row, cols.get(11), fmt);
        d.trainer = text(row, cols.get(12), fmt);
        d.teamLeader = text(row, cols.get(13), fmt);
        d.status = text(row, cols.get(14), fmt);
        d.remarks = text(row, cols.get(15), fmt);
        d.trainingLevel = text(row, cols.get(16), fmt);
        return d;
    }

    private CandidateRequest toRequest(RowData d, RecruitmentCategory category, Candidate existing,
                                       Map<String, AppUser> trainers, Map<String, AppUser> leaders) {
        WorkShift shift = d.shift == null ? null : parseEnum(WorkShift.values(), d.shift, WorkShift::getLabel, "Shift");
        CandidateStatus status = d.status == null ? null
                : parseEnum(CandidateStatus.values(), d.status, CandidateStatus::getLabel, "Statut");
        Long trainerId = d.trainer == null ? null : staffId(trainers, d.trainer, "Formatrice");
        Long leaderId = d.teamLeader == null ? null : staffId(leaders, d.teamLeader, "Team leader");
        TrainingLevel level = d.trainingLevel == null || !category.requiresTrainingLevel() ? null
                : parseEnum(TrainingLevel.values(), d.trainingLevel, TrainingLevel::getLabel, "Niveau");

        if (existing == null) {
            return new CandidateRequest(category, d.matricule, d.fullName, d.cin, d.birthDate, d.phone, d.email,
                    d.address, d.position, d.department, d.hireDate, d.educationLevel, shift, trainerId, leaderId,
                    status, d.remarks, level);
        }
        // Update: empty cells keep the current values.
        return new CandidateRequest(category,
                or(d.matricule, existing.getMatricule()), or(d.fullName, existing.getFullName()), existing.getCin(),
                or(d.birthDate, existing.getBirthDate()), or(d.phone, existing.getPhone()),
                or(d.email, existing.getEmail()), or(d.address, existing.getAddress()),
                or(d.position, existing.getPosition()), or(d.department, existing.getDepartment()),
                or(d.hireDate, existing.getHireDate()), or(d.educationLevel, existing.getEducationLevel()),
                or(shift, existing.getShift()),
                or(trainerId, existing.getTrainer() == null ? null : existing.getTrainer().getId()),
                or(leaderId, existing.getTeamLeader() == null ? null : existing.getTeamLeader().getId()),
                status, or(d.remarks, existing.getRemarks()), or(level, existing.getTrainingLevel()));
    }

    private void validate(CandidateRequest req) {
        Set<ConstraintViolation<CandidateRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            throw new RowException(violations.stream().map(ConstraintViolation::getMessage)
                    .distinct().sorted().collect(Collectors.joining(" ")));
        }
    }

    private Map<String, AppUser> staffIndex(Role role) {
        Map<String, AppUser> map = new HashMap<>();
        for (AppUser u : users.findAllByRoleInAndActiveTrueOrderByFullNameAsc(List.of(role))) {
            map.put(normalize(u.getUsername()), u);
            map.putIfAbsent(normalize(u.getFullName()), u);
        }
        return map;
    }

    private Long staffId(Map<String, AppUser> index, String value, String label) {
        AppUser u = index.get(normalize(value));
        if (u == null) throw new RowException(label + " introuvable : « " + value + " ».");
        return u.getId();
    }

    private static <E> E parseEnum(E[] values, String raw, java.util.function.Function<E, String> label, String field) {
        String key = normalize(raw);
        for (E v : values) {
            if (normalize(label.apply(v)).equals(key) || normalize(((Enum<?>) v).name()).equals(key)) return v;
        }
        throw new RowException(field + " invalide : « " + raw + " ».");
    }

    // ---------------------------------------------------------------- helpers

    private static int columnCount(RecruitmentCategory category) {
        return category.requiresTrainingLevel() ? HEADERS.length : BASE_COLUMNS;
    }

    private Sheet createDataSheet(XSSFWorkbook wb, RecruitmentCategory category) {
        int count = columnCount(category);
        Sheet sheet = wb.createSheet(category.getLabel());
        CellStyle head = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        head.setFont(font);
        head.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 0x15, (byte) 0x80, (byte) 0x3d}, null));
        head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        head.setAlignment(HorizontalAlignment.CENTER);
        head.setVerticalAlignment(VerticalAlignment.CENTER);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < count; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(head);
            sheet.setColumnWidth(i, WIDTHS[i] * 256);
        }
        CellStyle dateStyle = dateStyle(wb);
        CellStyle text = wb.createCellStyle();
        text.setDataFormat(wb.createDataFormat().getFormat("@"));
        for (int col : DATE_COLS) sheet.setDefaultColumnStyle(col, dateStyle);
        if (count > BASE_COLUMNS) sheet.setDefaultColumnStyle(17, dateStyle);
        sheet.setDefaultColumnStyle(0, text);
        sheet.setDefaultColumnStyle(2, text);
        sheet.setDefaultColumnStyle(4, text);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, count - 1));
        return sheet;
    }

    private void addValidations(Sheet sheet, RecruitmentCategory category, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        if (category.hasFixedPositions()) {
            // The list exceeds Excel's 255-char inline limit, so it lives on a hidden sheet.
            Workbook wb = sheet.getWorkbook();
            Sheet lists = wb.createSheet("Postes");
            List<String> positions = category.getPositions();
            for (int i = 0; i < positions.size(); i++) lists.createRow(i).createCell(0).setCellValue(positions.get(i));
            wb.setSheetHidden(wb.getSheetIndex(lists), true);
            DataValidation v = helper.createValidation(
                    helper.createFormulaListConstraint("Postes!$A$1:$A$" + positions.size()),
                    new CellRangeAddressList(1, lastRow, 7, 7));
            v.setShowErrorBox(true);
            sheet.addValidationData(v);
        }
        addList(sheet, helper, 11, lastRow, Arrays.stream(WorkShift.values()).map(WorkShift::getLabel).toArray(String[]::new));
        addList(sheet, helper, 14, lastRow, Arrays.stream(CandidateStatus.values()).map(CandidateStatus::getLabel).toArray(String[]::new));
        if (category.requiresTrainingLevel()) {
            addList(sheet, helper, 16, lastRow, Arrays.stream(TrainingLevel.values()).map(TrainingLevel::getLabel).toArray(String[]::new));
        }
    }

    private void addList(Sheet sheet, DataValidationHelper helper, int col, int lastRow, String[] values) {
        DataValidation v = helper.createValidation(helper.createExplicitListConstraint(values),
                new CellRangeAddressList(1, lastRow, col, col));
        v.setShowErrorBox(true);
        sheet.addValidationData(v);
    }

    private CellStyle dateStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
        return s;
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static boolean isBlank(Row row, DataFormatter fmt) {
        for (Cell c : row) if (!fmt.formatCellValue(c).isBlank()) return false;
        return true;
    }

    private static String text(Row row, Integer col, DataFormatter fmt) {
        if (col == null) return null;
        Cell c = row.getCell(col);
        if (c == null) return null;
        String v = (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c))
                ? new java.math.BigDecimal(String.valueOf(c.getNumericCellValue())).stripTrailingZeros().toPlainString()
                : fmt.formatCellValue(c);
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private static LocalDate date(Row row, Integer col, DataFormatter fmt, String label) {
        if (col == null) return null;
        Cell c = row.getCell(col);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (c.getCellType() == CellType.NUMERIC) {
            return DateUtil.getJavaDate(c.getNumericCellValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String v = fmt.formatCellValue(c).trim();
        if (v.isEmpty()) return null;
        try {
            return v.contains("/") ? LocalDate.parse(v, FR_DATE) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new RowException(label + " invalide : « " + v + " » (format JJ/MM/AAAA).");
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replace("*", "").replaceAll("[\\s_\\-/'’.]+", "");
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static final class RowData {
        String matricule, fullName, cin, phone, email, address, position, department, educationLevel,
                shift, trainer, teamLeader, status, remarks, trainingLevel;
        LocalDate birthDate, hireDate;
    }

    private static final class RowException extends RuntimeException {
        RowException(String message) { super(message); }
    }
}
