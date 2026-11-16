package com.taskmanager.service.mail;

import com.taskmanager.config.MailProperties;
import com.taskmanager.config.TaskManagerProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El canal por Gmail API tiene tres propiedades que hay que defender: el mensaje
 * compuesto debe ser un MIME legible (con el asunto acentuado decodificado), el
 * access token debe reutilizarse entre envíos y un fallo transitorio de
 * autenticación no debe romper el flujo de recuperación.
 */
class GmailApiMailSenderTest {

    private TaskManagerProperties taskManagerProperties;
    private MockRestServiceServer server;
    private GmailApiMailSender sender;

    @BeforeEach
    void setUp() {
        MailProperties mailProperties = new MailProperties();
        mailProperties.setProvider("gmail-api");
        mailProperties.setFrom("TaskManager <no-reply@gmail.com>");
        mailProperties.setClientId("client-id");
        mailProperties.setClientSecret("client-secret");
        mailProperties.setRefreshToken("refresh-token");

        taskManagerProperties = new TaskManagerProperties();
        taskManagerProperties.getBilling().setFrontendUrl("https://app.test");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new GmailApiMailSender(mailProperties, taskManagerProperties, builder);
    }

    @Test
    void componeUnMimeConAsuntoDestinatarioYCuerpoLegibles() throws Exception {
        VerificationCodeEmail message = VerificationCodeEmail.forCode("Ana", "482913", "https://app.test", 10);

        String raw = GmailApiMailSender.encodeRawMessage("TaskManager <no-reply@gmail.com>", "ana@example.test", message);

        assertNotNull(raw);
        MimeMessage parsed = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));

        assertEquals("Código de verificación - TaskManager", parsed.getSubject());
        assertEquals("ana@example.test",
                ((InternetAddress) parsed.getRecipients(Message.RecipientType.TO)[0]).getAddress());
        assertTrue(parsed.getContentType().startsWith("multipart/alternative"));

        MimeMultipart multipart = (MimeMultipart) parsed.getContent();
        String text = (String) ((MimeBodyPart) multipart.getBodyPart(0)).getContent();
        String html = (String) ((MimeBodyPart) multipart.getBodyPart(1)).getContent();

        assertTrue(text.contains("482913"));
        assertTrue(text.contains("expira en 10 minutos"));
        assertTrue(html.contains("482913"));
    }

    @Test
    void enviaElCodigoPorLaApiCacheandoElTokenDeAcceso() {
        server.expect(requestTo(GmailApiMailSender.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(tokenJson("token-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GmailApiMailSender.SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GmailApiMailSender.SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.sendVerificationCode("ana@example.test", "Ana", "482913", 10);
        sender.sendVerificationCode("luis@example.test", "Luis", "135790", 10);

        // Dos envíos y una sola renovación: el segundo reutiliza el token en caché.
        server.verify();
    }

    @Test
    void renuevaElTokenSiLaApiDeGmailRespondeUnauthorized() {
        server.expect(requestTo(GmailApiMailSender.TOKEN_URL))
                .andRespond(withSuccess(tokenJson("token-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GmailApiMailSender.SEND_URL))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(GmailApiMailSender.TOKEN_URL))
                .andRespond(withSuccess(tokenJson("token-2"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GmailApiMailSender.SEND_URL))
                .andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.sendVerificationCode("ana@example.test", "Ana", "482913", 10);

        server.verify();
    }

    @Test
    void noLanzaExcepcionesCuandoFaltanLasCredenciales() {
        MailProperties sinCredenciales = new MailProperties();
        sinCredenciales.setProvider("gmail-api");
        GmailApiMailSender sinConfigurar = new GmailApiMailSender(sinCredenciales, taskManagerProperties, RestClient.builder());

        assertDoesNotThrow(() -> sinConfigurar.sendVerificationCode("ana@example.test", "Ana", "482913", 10));
    }

    private static String tokenJson(String token) {
        return "{\"access_token\":\"" + token + "\",\"expires_in\":3600}";
    }
}
