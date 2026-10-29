package com.adient.training.service;

import com.adient.training.dto.Dtos.CreateUserRequest;
import com.adient.training.dto.Dtos.RoleDto;
import com.adient.training.dto.RecruitmentDtos.ImportError;
import com.adient.training.dto.RecruitmentDtos.ImportResult;
import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.security.AuthUser;
import jakarta.validation.Validator;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserExcelService {
    private static final int MAX_ROWS = 5000;
    private static final String[] HEADERS = {
            "Identifiant *", "Nom complet *", "E-mail", "Rôle *", "Mot de passe temporaire *", "Type de travail"
    };
    private final UserService users;
    private final Validator validator;
    private final I18nService i18n;

    public UserExcelService(UserService users, Validator validator, I18nService i18n) {
        this.users = users;
        this.validator = validator;
        this.i18n = i18n;
    }

    public byte[] template(AuthUser actor) {
        requireManager(actor);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = dataSheet(workbook, HEADERS);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int column = 0; column < HEADERS.length; column++) sheet.setDefaultColumnStyle(column, text);
            String[] roles = users.assignableRoles(actor).stream().map(RoleDto::code).toArray(String[]::new);
            DataValidationHelper helper = sheet.getDataValidationHelper();
            Sheet roleSheet = workbook.createSheet("_roles");
            for (int i = 0; i < roles.length; i++) roleSheet.createRow(i).createCell(0).setCellValue(roles[i]);
            var roleRange = workbook.createName();
            roleRange.setNameName("AssignableRoles");
            roleRange.setRefersToFormula("'_roles'!$A$1:$A$" + roles.length);
            workbook.setSheetHidden(workbook.getSheetIndex(roleSheet), true);
            DataValidation validation = helper.createValidation(helper.createFormulaListConstraint("AssignableRoles"),
                    new CellRangeAddressList(1, MAX_ROWS, 3, 3));
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox(i18n.translate("Rôle non autorisé"), i18n.translate("Choisissez un rôle dans la liste."));
            sheet.addValidationData(validation);
            DataValidation workTypes = helper.createValidation(
                    helper.createExplicitListConstraint(new String[]{"CADRE", "DIRECT", "INDIRECT"}),
                    new CellRangeAddressList(1, MAX_ROWS, 5, 5));
            workTypes.setShowErrorBox(true);
            workTypes.setErrorStyle(DataValidation.ErrorStyle.STOP);
            workTypes.createErrorBox(i18n.translate("Type de travail"), i18n.translate("Type de travail invalide."));
            sheet.addValidationData(workTypes);
            Sheet help = workbook.createSheet(i18n.translate("Instructions"));
            String[] instructions = {
                    "Import de nouveaux comptes utilisateurs",
                    "Remplir la feuille Utilisateurs à partir de la ligne 2. Maximum : " + MAX_ROWS + " lignes, 10 Mo.",
                    "Obligatoires : Identifiant, Nom complet, Rôle, Mot de passe temporaire. E-mail facultatif.",
                    "Identifiant : 1 à 50 caractères, lettres, chiffres, point, tiret ou underscore.",
                    i18n.translate("Rôles autorisés :") + " " + users.assignableRoles(actor).stream()
                            .map(role -> role.code() + " (" + i18n.translate(role.label()) + ")").collect(Collectors.joining(", ")),
                    "Les noms d’utilisateur existants sont refusés, sans distinction de casse. Aucun compte n’est modifié.",
                    "Mot de passe : 8 à 72 caractères, majuscule, minuscule, chiffre et caractère spécial.",
                    "Chaque nouveau compte est actif et doit changer son mot de passe à la première connexion.",
                    "Les lignes valides sont créées ; chaque erreur indique le numéro de ligne Excel et son motif.",
                    "Les formules ne sont pas acceptées. Saisissez uniquement des valeurs dans les cellules.",
                    "Ce fichier contient des mots de passe temporaires : conservez-le de façon sécurisée puis supprimez-le après utilisation.",
                    "L’export ne contient aucun mot de passe et ne permet pas de modifier les comptes existants."
            };
            for (int i = 0; i < instructions.length; i++) help.createRow(i).createCell(0).setCellValue(i18n.translate(instructions[i]));
            help.setRightToLeft(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage().equals("ar"));
            help.setColumnWidth(0, 120 * 256);
            return bytes(workbook);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de générer le modèle utilisateurs.");
        }
    }

    public byte[] export(AuthUser actor) {
        requireManager(actor);
        return exportUsers(users.list());
    }

    public byte[] exportSelection(AuthUser actor, List<Long> ids) {
        requireManager(actor);
        if (ids == null || ids.isEmpty() || ids.size() > 10000
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw badFile("Sélection ou action invalide.");
        }
        Set<Long> selected = new HashSet<>(ids);
        var selectedUsers = users.list().stream().filter(user -> selected.contains(user.id())).toList();
        if (selectedUsers.size() != selected.size()) throw badFile("Utilisateur introuvable.");
        return exportUsers(selectedUsers);
    }

    private byte[] exportUsers(List<com.adient.training.dto.Dtos.UserDto> selectedUsers) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = dataSheet(workbook, new String[]{
                    "Identifiant", "Nom complet", "E-mail", "Rôle", "Statut",
                    "Mot de passe à changer", "Dernière connexion", "Créé le", "Type de travail"
            });
            int line = 1;
            for (var user : selectedUsers) {
                Row row = sheet.createRow(line++);
                String[] values = {user.username(), user.fullName(), user.email(), user.role(),
                        i18n.translate(user.active() ? "Actif" : "Inactif"), i18n.translate(user.mustChangePassword() ? "Oui" : "Non"),
                        user.lastLoginAt() == null ? "" : user.lastLoginAt().toString(),
                        user.createdAt() == null ? "" : user.createdAt().toString(), user.workType()};
                for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, line - 1), 0, 8));
            return bytes(workbook);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d’exporter les utilisateurs.");
        }
    }

    // No enclosing transaction: each UserService.create commits or rolls back its own row.
    public ImportResult importFile(AuthUser actor, MultipartFile file) {
        requireManager(actor);
        if (file == null || file.isEmpty()) throw badFile("Aucun fichier reçu.");
        if (file.getSize() > 10 * 1024 * 1024) throw badFile("Fichier trop volumineux (10 Mo maximum).");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw badFile("Format non supporté : utilisez un fichier Excel .xlsx.");
        }
        try (var input = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw badFile("La feuille Utilisateurs est manquante.");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            Map<Integer, Integer> columns = columns(sheet.getRow(0), formatter);
            if (sheet.getLastRowNum() > MAX_ROWS) throw badFile("Maximum " + MAX_ROWS + " lignes par fichier.");
            int created = 0;
            List<ImportError> errors = new ArrayList<>();
            Set<String> usernames = new HashSet<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlank(row, formatter)) continue;
                try {
                    String username = value(row, columns.get(0), formatter).trim();
                    if (!usernames.add(username.toLowerCase(Locale.ROOT))) {
                        throw badFile("Identifiant en double dans le fichier.");
                    }
                    CreateUserRequest request = new CreateUserRequest(username,
                            value(row, columns.get(1), formatter).trim(),
                            value(row, columns.get(2), formatter).trim(),
                            role(i18n.frenchHeader(value(row, columns.get(3), formatter))),
                            value(row, columns.get(4), formatter),
                            workType(i18n.frenchHeader(value(row, columns.get(5), formatter))));
                    String violations = validator.validate(request).stream().map(v -> v.getMessage())
                            .map(i18n::translate).distinct().sorted().collect(Collectors.joining(" "));
                    if (!violations.isEmpty()) throw badFile(violations);
                    users.create(actor, request);
                    created++;
                } catch (ApiException ex) {
                    errors.add(new ImportError(index + 1, ex.getMessage()));
                } catch (DataIntegrityViolationException ex) {
                    errors.add(new ImportError(index + 1, "Conflit : cet identifiant existe déjà."));
                }
            }
            return new ImportResult(created, 0, errors.size(), errors);
        } catch (IOException | EncryptedDocumentException | NotOfficeXmlFileException ex) {
            throw badFile("Impossible de lire le fichier Excel : utilisez un modèle .xlsx valide, non chiffré.");
        }
    }

    private Map<Integer, Integer> columns(Row header, DataFormatter formatter) {
        if (header == null) throw badFile("En-têtes manquants. Utilisez le modèle utilisateurs.");
        Map<Integer, Integer> result = new HashMap<>();
        for (Cell cell : header) {
            String name = normalize(formatter.formatCellValue(cell).trim());
            for (int i = 0; i < HEADERS.length; i++) {
                if (((i == 0 && name.equals("matricule"))
                        || i18n.headerAliases(HEADERS[i]).stream().map(UserExcelService::normalize).anyMatch(name::equals))
                        && result.putIfAbsent(i, cell.getColumnIndex()) != null) {
                    throw badFile("Colonne en double : " + i18n.translate(HEADERS[i]));
                }
            }
        }
        for (int required : new int[]{0, 1, 3, 4}) {
            if (!result.containsKey(required)) throw badFile("Colonne manquante : " + i18n.translate(HEADERS[required]) + ". Utilisez le modèle utilisateurs.");
        }
        return result;
    }

    private static Role role(String value) {
        return Arrays.stream(Role.values())
                .filter(role -> normalize(role.name()).equals(normalize(value))
                        || normalize(role.getLabel()).equals(normalize(value)))
                .findFirst().orElseThrow(() -> badFile("Rôle manquant ou inconnu. Utilisez la liste du modèle."));
    }

    private static String workType(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (!Set.of("CADRE", "DIRECT", "INDIRECT").contains(normalized)) {
            throw badFile("Type de travail invalide.");
        }
        return normalized;
    }

    private static String value(Row row, Integer column, DataFormatter formatter) {
        if (column == null) return "";
        Cell cell = row.getCell(column);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
            throw badFile("Les formules et cellules en erreur ne sont pas acceptées.");
        }
        return formatter.formatCellValue(cell);
    }

    private static boolean isBlank(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace("*", "").replace('_', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Sheet dataSheet(XSSFWorkbook workbook, String[] headers) {
        Sheet sheet = workbook.createSheet(i18n.translate("Utilisateurs"));
        sheet.setRightToLeft(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage().equals("ar"));
        sheet.createFreezePane(0, 1);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(28);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(i18n.translate(headers[i]));
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, 30 * 256);
        }
        return sheet;
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private static void requireManager(AuthUser actor) {
        if (!actor.role().isUserManager()) throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé.");
    }

    private static ApiException badFile(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
