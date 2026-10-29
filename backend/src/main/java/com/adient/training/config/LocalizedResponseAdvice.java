package com.adient.training.config;

import com.adient.training.service.I18nService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Set;

@RestControllerAdvice
public class LocalizedResponseAdvice implements ResponseBodyAdvice<Object> {
    private static final Set<String> TRANSLATED_FIELDS = Set.of("message", "reason");
    private final I18nService i18n;
    private final ObjectMapper mapper;

    public LocalizedResponseAdvice(I18nService i18n, ObjectMapper mapper) {
        this.i18n = i18n;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || request.getURI().getPath().equals("/api/i18n")) return body;
        JsonNode tree = mapper.valueToTree(body);
        translate(tree);
        return tree;
    }

    private void translate(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(field -> {
                if (TRANSLATED_FIELDS.contains(field.getKey()) && field.getValue().isTextual()) {
                    object.put(field.getKey(), i18n.translate(field.getValue().textValue()));
                } else {
                    translate(field.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::translate);
        }
    }
}
