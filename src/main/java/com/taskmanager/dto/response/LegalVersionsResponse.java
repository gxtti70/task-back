package com.taskmanager.dto.response;

/**
 * Fuente única de verdad de las versiones legales vigentes. El frontend la
 * consume para mostrar los documentos y para enviar la versión aceptada en el
 * registro, evitando que front y back diverjan.
 */
public record LegalVersionsResponse(
        String termsVersion,
        String privacyVersion,
        String cookiesVersion,
        String effectiveDate,
        String controllerName,
        String controllerTaxId,
        String controllerAddress,
        String privacyEmail) {
}
