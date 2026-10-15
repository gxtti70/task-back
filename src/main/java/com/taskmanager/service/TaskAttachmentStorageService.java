package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.entity.TaskAttachment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class TaskAttachmentStorageService {
    private static final Duration URL_TTL = Duration.ofMinutes(5);
    private final TaskManagerProperties properties;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectProvider<S3Presigner> s3Presigner;
    private final ObjectProvider<DefaultCredentialsProvider> credentialsProvider;
    private final ObjectMapper objectMapper;

    public UploadTarget uploadTarget(TaskAttachment attachment, long maxBytes) {
        if (properties.getBilling().isSimulate()) {
            return new UploadTarget("/api/tasks/" + attachment.getTask().getId() + "/attachments/"
                    + attachment.getId() + "/content", Map.of());
        }
        return presignedPost(attachment, maxBytes);
    }

    public String downloadUrl(TaskAttachment attachment) {
        if (properties.getBilling().isSimulate()) {
            return "/api/tasks/" + attachment.getTask().getId() + "/attachments/" + attachment.getId() + "/content";
        }
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket()).key(attachment.getStorageKey())
                .responseContentDisposition("attachment; filename=\"" + safeFilename(attachment.getFilename()) + "\"").build();
        return s3Presigner.getObject().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(URL_TTL).getObjectRequest(get).build()).url().toString();
    }

    public Path localPath(TaskAttachment attachment) {
        Path root = Path.of(properties.getStorage().getLocalDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(attachment.getStorageKey()).normalize();
        if (!target.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de almacenamiento no válida");
        return target;
    }

    public void storeLocal(TaskAttachment attachment, InputStream input) throws IOException {
        Path target = localPath(attachment);
        Files.createDirectories(target.getParent());
        long written = 0;
        try (OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (written > attachment.getSizeBytes()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo excede el tamaño declarado");
                }
                output.write(buffer, 0, read);
            }
            if (written != attachment.getSizeBytes()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño del archivo no coincide con el declarado");
            }
        } catch (RuntimeException | IOException error) {
            Files.deleteIfExists(target);
            throw error;
        }
    }

    public long remoteObjectSize(TaskAttachment attachment) {
        try {
            return s3Client.getObject().headObject(HeadObjectRequest.builder().bucket(bucket()).key(attachment.getStorageKey()).build())
                    .contentLength();
        } catch (S3Exception error) {
            if (error.statusCode() == 404) return -1;
            throw error;
        }
    }

    private UploadTarget presignedPost(TaskAttachment attachment, long maxBytes) {
        String bucket = bucket();
        Instant now = Instant.now();
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now);
        String region = properties.getStorage().getS3().getRegion();
        AwsCredentials credentials = credentialsProvider.getObject().resolveCredentials();
        String sessionToken = credentials instanceof AwsSessionCredentials session ? session.sessionToken() : null;
        String scope = date + "/" + region + "/s3/aws4_request";
        String credential = credentials.accessKeyId() + "/" + scope;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", attachment.getStorageKey());
        fields.put("Content-Type", attachment.getContentType());
        fields.put("success_action_status", "201");
        fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", timestamp);
        if (sessionToken != null) fields.put("x-amz-security-token", sessionToken);

        List<Object> conditions = new ArrayList<>();
        conditions.add(Map.of("bucket", bucket));
        conditions.add(Map.of("key", attachment.getStorageKey()));
        conditions.add(Map.of("Content-Type", attachment.getContentType()));
        conditions.add(Map.of("success_action_status", "201"));
        conditions.add(Map.of("x-amz-algorithm", "AWS4-HMAC-SHA256"));
        conditions.add(Map.of("x-amz-credential", credential));
        conditions.add(Map.of("x-amz-date", timestamp));
        if (sessionToken != null) conditions.add(Map.of("x-amz-security-token", sessionToken));
        conditions.add(List.of("content-length-range", 1, maxBytes));
        Map<String, Object> policy = Map.of("expiration", DateTimeFormatter.ISO_INSTANT.format(now.plus(URL_TTL)), "conditions", conditions);
        String encodedPolicy;
        try {
            encodedPolicy = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(policy));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo generar la política de carga S3", error);
        }
        fields.put("policy", encodedPolicy);
        fields.put("x-amz-signature", hex(hmac(signingKey(credentials.secretAccessKey(), date, region), encodedPolicy)));
        return new UploadTarget("https://" + bucket + ".s3." + region + ".amazonaws.com/", Map.copyOf(fields));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    private byte[] signingKey(String secret, String date, String region) {
        byte[] dateKey = hmac(("AWS4" + secret).getBytes(java.nio.charset.StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, "s3");
        return hmac(serviceKey, "aws4_request");
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo firmar la política de carga S3", error);
        }
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo firmar la política de carga S3", error);
        }
    }

    public void delete(TaskAttachment attachment) throws IOException {
        if (properties.getBilling().isSimulate()) {
            Files.deleteIfExists(localPath(attachment));
            return;
        }
        s3Client.getObject().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(attachment.getStorageKey()).build());
    }

    private String bucket() {
        String bucket = properties.getStorage().getS3().getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("ATTACHMENTS_S3_BUCKET debe configurarse cuando billing.simulate=false");
        }
        return bucket;
    }

    private String safeFilename(String filename) {
        return filename.replaceAll("[\"\\r\\n]", "_");
    }

    public record UploadTarget(String url, Map<String, String> fields) { }
}
