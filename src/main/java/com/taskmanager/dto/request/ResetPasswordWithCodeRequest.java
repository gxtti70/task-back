package com.taskmanager.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Restablecimiento de contraseña con el código de 6 dígitos recibido por correo.
 */
@Data
public class ResetPasswordWithCodeRequest {
    @NotBlank(message = "El email no puede estar vacío")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotBlank(message = "El código no puede estar vacío")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String code;

    @NotBlank(message = "La contraseña no puede estar vacía")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;
}
