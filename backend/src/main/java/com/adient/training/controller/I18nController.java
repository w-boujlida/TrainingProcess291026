package com.adient.training.controller;

import com.adient.training.service.I18nService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class I18nController {
    private final I18nService i18n;

    public I18nController(I18nService i18n) {
        this.i18n = i18n;
    }

    @GetMapping("/api/i18n")
    public Map<String, Map<String, String>> catalogs() {
        return i18n.catalogs();
    }
}
