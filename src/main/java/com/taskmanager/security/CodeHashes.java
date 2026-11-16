package com.taskmanager.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Hash de los códigos de verificación de 6 dígitos.
 * <p>
 * Un código de 6 dígitos solo tiene un millón de combinaciones: almacenado en texto
 * plano o con un hash rápido, una filtración de la base de datos permitiría
 * enumerar todos los códigos válidos en segundos. Por eso se usa HMAC-SHA256 con un
 * pepper que solo existe en el servidor.
 */
public final class CodeHashes {

    private static final String ALGORITHM = "HmacSHA256";

    private CodeHashes() { }

    /**
     * Calcula el HMAC-SHA256 del código usando el pepper del servidor.
     *
     * @return hash en hexadecimal, 64 caracteres
     */
    public static String hmacSha256(String pepper, String code) {
        if (pepper == null || pepper.isEmpty()) {
            throw new IllegalStateException("El pepper del código de verificación no está configurado.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo calcular el hash del código de verificación.", error);
        }
    }

    /**
     * Compara dos hashes en tiempo constante para no filtrar información por temporización.
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
