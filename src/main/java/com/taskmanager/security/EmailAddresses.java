package com.taskmanager.security;

import java.util.Locale;

public final class EmailAddresses {
    private EmailAddresses() { }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
