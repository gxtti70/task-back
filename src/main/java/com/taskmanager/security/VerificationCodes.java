package com.taskmanager.security;

import java.security.SecureRandom;

/**
 * Generación de códigos numéricos de un solo uso.
 */
public final class VerificationCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BOUND = 1_000_000;

    private VerificationCodes() { }

    /**
     * Devuelve un código de 6 dígitos con relleno de ceros a la izquierda.
     * <p>
     * Usa {@link SecureRandom#nextInt(int)} con el rango exacto para evitar el sesgo
     * que introduce el módulo sobre un múltiplo de mil.
     */
    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(BOUND));
    }
}
