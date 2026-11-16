package com.taskmanager.service.mail;

/**
 * Contenido del correo de código de verificación, independiente del canal de envío.
 * <p>
 * El código se muestra en un bloque monoespaciado grande para que sea legible
 * en el teléfono y se pueda transcribir sin errores.
 */
public record VerificationCodeEmail(String subject, String html, String text) {

    public static VerificationCodeEmail forCode(String fullName, String code, String appUrl, int expiresInMinutes) {
        String name = fullName == null ? "" : fullName.trim();
        String greeting = name.isEmpty() ? "Hola" : "Hola " + name;
        String minutes = String.valueOf(Math.max(1, expiresInMinutes));
        String appLink = appUrl == null || appUrl.isBlank() ? "" : appUrl.trim();

        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#18181b;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border:1px solid #e4e4e7;border-radius:12px;overflow:hidden;">
                          <tr>
                            <td style="padding:28px 32px 8px 32px;">
                              <p style="margin:0;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#71717a;">TaskManager</p>
                              <h1 style="margin:8px 0 0 0;font-size:20px;line-height:1.3;color:#18181b;">Código de verificación</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 32px 0 32px;font-size:15px;line-height:1.6;color:#3f3f46;">
                              <p style="margin:0 0 20px 0;">%s, usaste este código para restablecer la contraseña de tu cuenta.</p>
                              <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;">
                                <tr>
                                  <td align="center" style="padding:22px 0;background:#fafafa;border:1px solid #e4e4e7;border-radius:10px;">
                                    <span style="font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:34px;font-weight:700;letter-spacing:.32em;color:#18181b;">%s</span>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:20px 0 0 0;font-size:14px;line-height:1.6;color:#52525b;">
                                El código expira en %s minutos y solo puede usarse una vez.
                              </p>
                            </td>
                          </tr>
                          %s
                          <tr>
                            <td style="padding:24px 32px 28px 32px;border-top:1px solid #f4f4f5;">
                              <p style="margin:0;font-size:12px;line-height:1.6;color:#a1a1aa;">
                                Si no solicitaste restablecer tu contraseña, ignora este mensaje y no compartas el código con nadie.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(greeting), escape(code), minutes, appLink.isEmpty() ? "" : appLinkBlock(appLink));

        String text = """
                %s,

                Tu código para restablecer la contraseña en TaskManager es:

                %s

                El código expira en %s minutos y solo puede usarse una vez.

                %s

                Si no solicitaste restablecer tu contraseña, ignora este mensaje y no compartas el código con nadie.
                """.formatted(greeting, code, minutes,
                appLink.isEmpty() ? "" : "Abre " + appLink + " para continuar.");

        return new VerificationCodeEmail("Código de verificación - TaskManager", html, text);
    }

    private static String appLinkBlock(String appUrl) {
        return """
                <tr>
                  <td style="padding:22px 32px 0 32px;">
                    <a href="%s" style="display:inline-block;background:#18181b;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;padding:12px 20px;border-radius:8px;">Restablecer contraseña</a>
                  </td>
                </tr>
                """.formatted(escape(appUrl));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
