package com.adient.training.service;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class I18nService {
    private static final List<String> LANGUAGES = List.of("fr", "en", "it", "ar");
    private final Map<String, Map<String, String>> catalogs;

    public I18nService() throws IOException {
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        LANGUAGES.forEach(language -> loaded.put(language, new LinkedHashMap<>()));
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("i18n/translations.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] columns = line.split("\t", -1);
                if (columns.length != 4) throw new IOException("Invalid translation row: " + columns[0]);
                for (int i = 0; i < LANGUAGES.size(); i++) {
                    if (columns[i].isBlank()) throw new IOException("Empty translation: " + columns[0]);
                    String previous = loaded.get(LANGUAGES.get(i)).put(columns[0], columns[i]);
                    if (previous != null) throw new IOException("Duplicate translation: " + columns[0]);
                }
            }
        }
        Map<String, Map<String, String>> immutable = new LinkedHashMap<>();
        loaded.forEach((language, entries) -> immutable.put(language, Collections.unmodifiableMap(entries)));
        catalogs = Collections.unmodifiableMap(immutable);
    }

    public Map<String, Map<String, String>> catalogs() {
        return catalogs;
    }

    public String translate(String source) {
        return translate(source, LocaleContextHolder.getLocaleContext() == null ? Locale.FRENCH : LocaleContextHolder.getLocale());
    }

    public String translate(String source, Locale locale) {
        if (source == null) return null;
        var catalog = catalogs.getOrDefault(locale.getLanguage(), catalogs.get("fr"));
        String exact = catalog.get(source);
        if (exact != null) return exact;
        // Dynamic error messages contain identifiers and other user data; copy captures unchanged.
        for (var entry : catalog.entrySet()) {
            if (!entry.getKey().contains("{0}")) continue;
            String[] parts = entry.getKey().split("\\{\\d+}", -1);
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append("(.*?)");
                regex.append(Pattern.quote(parts[i]));
            }
            var matcher = Pattern.compile(regex.append("$").toString(), Pattern.DOTALL).matcher(source);
            if (matcher.matches()) {
                return Pattern.compile("\\{(\\d+)}").matcher(entry.getValue()).replaceAll(
                        placeholder -> java.util.regex.Matcher.quoteReplacement(
                                matcher.group(Integer.parseInt(placeholder.group(1)) + 1)));
            }
        }
        return source;
    }

    public String frenchHeader(String header) {
        for (var catalog : catalogs.values()) {
            for (var entry : catalog.entrySet()) {
                if (entry.getValue().equals(header)) return entry.getKey();
            }
        }
        return header;
    }

    public List<String> headerAliases(String source) {
        return catalogs.values().stream().map(catalog -> catalog.getOrDefault(source, source)).distinct().toList();
    }
}
