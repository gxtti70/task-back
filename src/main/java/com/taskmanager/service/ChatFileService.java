package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.ChatMessageResponse;
import com.taskmanager.entity.Message;
import com.taskmanager.repository.ChannelMemberRepository;
import com.taskmanager.repository.ChannelRepository;
import com.taskmanager.repository.MessageRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatFileService {
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024; // 25 MB

    private final MessageRepository messages;
    private final ChannelRepository channels;
    private final ChannelMemberRepository channelMembers;
    private final UserRepository users;
    private final TaskManagerProperties properties;

    @Transactional
    public ChatMessageResponse saveFileMessage(UUID channelId, MultipartFile file, String body, String actorEmail) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "El archivo excede los 25 MB permitidos");
        }

        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var channel = channels.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal no encontrado"));

        boolean allowed;
        if (channel.getProject() != null) {
            allowed = RoleNames.normalize(actor.getRole()).equals("ADMIN")
                    || channel.getProject().getOwner().getId().equals(actor.getId());
        } else {
            allowed = channelMembers.existsByChannel_IdAndUser_Id(channel.getId(), actor.getId());
        }
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debes pertenecer al canal para enviar archivos");
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String storageKey = UUID.randomUUID().toString();
        String fileName = sanitizeFilename(file.getOriginalFilename());

        try {
            storeFile(storageKey, file.getInputStream(), file.getSize());
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el archivo");
        }

        Message message = messages.save(Message.builder()
                .channel(channel)
                .sender(actor)
                .body(body == null ? "" : body.trim())
                .fileName(fileName)
                .fileContentType(contentType)
                .fileSizeBytes(file.getSize())
                .fileStorageKey(storageKey)
                .messageType("FILE")
                .build());

        return toResponse(message);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadFile(UUID channelId, UUID messageId, String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));

        if (!message.getChannel().getId().equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no pertenece a ese canal");
        }

        boolean allowed;
        if (message.getChannel().getProject() != null) {
            allowed = RoleNames.normalize(actor.getRole()).equals("ADMIN")
                    || message.getChannel().getProject().getOwner().getId().equals(actor.getId());
        } else {
            allowed = channelMembers.existsByChannel_IdAndUser_Id(message.getChannel().getId(), actor.getId());
        }
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este archivo");
        }

        if ("FILE".equals(message.getMessageType()) == false || message.getFileStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene un archivo adjunto");
        }

        var path = localPath(message);
        var resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no disponible");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(message.getFileContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeHeaderFilename(message.getFileName()) + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @Transactional
    public void deleteFile(UUID channelId, UUID messageId, String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));

        if (!message.getChannel().getId().equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no pertenece a ese canal");
        }

        boolean admin = RoleNames.normalize(actor.getRole()).equals("ADMIN");
        if (!message.getSender().getId().equals(actor.getId()) && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien envió el archivo o un administrador puede eliminarlo");
        }

        if (message.getFileStorageKey() != null) {
            try {
                Files.deleteIfExists(localPath(message));
            } catch (IOException ignored) {
            }
        }

        messages.delete(message);
    }

    private void storeFile(String storageKey, java.io.InputStream input, long declaredSize) throws IOException {
        var target = localPathByKey(storageKey);
        Files.createDirectories(target.getParent());
        long written = 0;
        try (var output = Files.newOutputStream(target)) {
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (written > MAX_FILE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo excede el tamaño permitido");
                }
                output.write(buffer, 0, read);
            }
        } catch (RuntimeException | IOException error) {
            Files.deleteIfExists(target);
            throw error;
        }
    }

    private java.nio.file.Path localPath(Message message) {
        return localPathByKey(message.getFileStorageKey());
    }

    private java.nio.file.Path localPathByKey(String storageKey) {
        var root = java.nio.file.Path.of(properties.getStorage().getLocalDirectory()).toAbsolutePath().normalize();
        var target = root.resolve("chat").resolve(storageKey).normalize();
        if (!target.startsWith(root.resolve("chat"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de almacenamiento no válida");
        }
        return target;
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "archivo";
        String filename = name.trim().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            return "archivo";
        }
        return filename;
    }

    private String safeHeaderFilename(String filename) {
        return filename.replaceAll("[\"\\r\\n]", "_");
    }

    private ChatMessageResponse toResponse(Message message) {
        return new ChatMessageResponse(
                message.getId(), message.getBody(), message.getSender().getId(),
                message.getSender().getFullName(), message.getSender().getEmail(),
                message.getCreatedAt(), message.getMessageType(),
                message.getFileName(), message.getFileContentType(), message.getFileSizeBytes(),
                "/api/channels/" + message.getChannel().getId() + "/messages/" + message.getId() + "/file"
        );
    }
}
