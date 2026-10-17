package com.taskmanager.controller;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.LegalVersionsResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalControllerTest {

    @Test
    void exposesConfiguredLegalVersionsAsSingleSourceOfTruth() {
        TaskManagerProperties properties = new TaskManagerProperties();
        properties.getLegal().setTermsVersion("2.1");
        properties.getLegal().setPrivacyVersion("2.2");
        properties.getLegal().setCookiesVersion("1.4");
        properties.getLegal().setEffectiveDate("2026-11-01");
        properties.getLegal().setControllerName("Acme S.A.S.");
        properties.getLegal().setPrivacyEmail("privacidad@acme.test");

        LegalVersionsResponse response = new LegalController(properties).versions();

        assertEquals("2.1", response.termsVersion());
        assertEquals("2.2", response.privacyVersion());
        assertEquals("1.4", response.cookiesVersion());
        assertEquals("2026-11-01", response.effectiveDate());
        assertEquals("Acme S.A.S.", response.controllerName());
        assertEquals("privacidad@acme.test", response.privacyEmail());
    }
}
