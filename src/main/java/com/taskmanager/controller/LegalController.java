package com.taskmanager.controller;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.LegalVersionsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público con las versiones legales vigentes y los datos del
 * responsable del tratamiento. El registro valida contra esta misma fuente.
 */
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalController {
    private final TaskManagerProperties properties;

    @GetMapping("/versions")
    public LegalVersionsResponse versions() {
        TaskManagerProperties.Legal legal = properties.getLegal();
        return new LegalVersionsResponse(
                legal.getTermsVersion(),
                legal.getPrivacyVersion(),
                legal.getCookiesVersion(),
                legal.getEffectiveDate(),
                legal.getControllerName(),
                legal.getControllerTaxId(),
                legal.getControllerAddress(),
                legal.getPrivacyEmail());
    }
}
