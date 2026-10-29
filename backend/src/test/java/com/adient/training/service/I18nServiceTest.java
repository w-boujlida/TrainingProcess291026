package com.adient.training.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class I18nServiceTest {
    private final I18nService service = new I18nService();

    I18nServiceTest() throws Exception {}

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void allCatalogsHaveCompleteEntriesAndIdenticalPlaceholders() {
        var french = service.catalogs().get("fr");
        assertThat(french.size()).isGreaterThan(600);
        service.catalogs().forEach((language, entries) -> {
            assertThat(entries.keySet()).containsExactlyInAnyOrderElementsOf(french.keySet());
            entries.forEach((source, translated) -> {
                assertThat(translated).isNotBlank();
                assertThat(placeholders(translated)).as(language + ": " + source)
                        .containsExactlyInAnyOrderElementsOf(placeholders(source));
            });
        });
    }

    @Test
    void selectsLanguageAndDefaultsToFrenchWithoutTranslatingUserData() {
        assertThat(service.translate("Utilisateur introuvable.")).isEqualTo("Utilisateur introuvable.");
        assertThat(service.translate("Utilisateur introuvable.", Locale.ENGLISH)).isEqualTo("User not found.");
        assertThat(service.translate("Utilisateur introuvable.", Locale.ITALIAN)).isEqualTo("Utente non trovato.");
        assertThat(service.translate("Utilisateur introuvable.", Locale.forLanguageTag("ar"))).isEqualTo("المستخدم غير موجود.");
        assertThat(service.translate("Utilisateur introuvable.", Locale.GERMAN)).isEqualTo("Utilisateur introuvable.");
        assertThat(service.translate("Nom libre / Alex", Locale.ENGLISH)).isEqualTo("Nom libre / Alex");
        assertThat(service.translate("Colonne en double : prénom {1} $\\", Locale.ENGLISH))
                .isEqualTo("Duplicate column: prénom {1} $\\");
    }

    @Test
    void localizedHeadersRemainCompatibleAcrossLanguages() {
        assertThat(service.headerAliases("Nom et prénom *")).contains("Full name *", "Nome e cognome *", "الاسم واللقب *");
        assertThat(service.frenchHeader("Preparazione")).isEqualTo("Préparation");
    }

    @Test
    void auditActionLabelsAndDateErrorsHaveTranslations() {
        var actions = java.util.List.of("Connexion", "Déconnexion", "Consultation", "Création", "Modification",
                "Suppression", "Signature", "Import Excel", "Export Excel", "Modèle Excel",
                "Changement du mot de passe", "Réinitialisation du mot de passe", "Requête");
        service.catalogs().forEach((language, entries) -> assertThat(entries.keySet()).containsAll(actions));
        assertThat(service.translate("Date of birth invalide : « valeur libre » (format JJ/MM/AAAA).", Locale.ENGLISH))
                .isEqualTo("Invalid Date of birth: “valeur libre” (DD/MM/YYYY format).");
    }

    private static java.util.List<String> placeholders(String text) {
        return Pattern.compile("\\{\\d+}").matcher(text).results().map(result -> result.group()).toList();
    }
}
