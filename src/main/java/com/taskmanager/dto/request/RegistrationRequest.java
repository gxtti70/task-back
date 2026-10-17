package com.taskmanager.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Alta pública de usuario. Extiende {@link RegisterRequest} con la autorización
 * previa, expresa e informada exigida por la Ley 1581 de 2012 (art. 9).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class RegistrationRequest extends RegisterRequest {

    @AssertTrue(message = "Debes aceptar los Términos y Condiciones")
    private boolean acceptTerms;

    @AssertTrue(message = "Debes aceptar la Política de Tratamiento de Datos Personales")
    private boolean acceptPrivacy;

    private String termsVersion;

    private String privacyVersion;
}
