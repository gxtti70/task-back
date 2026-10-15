package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.request.CreateTaskAttachmentRequest;
import com.taskmanager.dto.response.TaskAttachmentResponse;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskAttachment;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.TaskAttachmentRepository;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.RoleNames;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskAttachmentService {
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private final TaskAttachmentRepository attachments;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProjectMemberRepository members;
    private final FeatureGate featureGate;
    private final TaskManagerProperties properties;
    private final TaskAttachmentStorageService storage;

    @Transactional
    public TaskAttachmentResponse prepare(UUID taskId, CreateTaskAttachmentRequest request, String email) {
        User user = currentUser(email);
        Task task = requireTaskAccess(taskId, user, true);
        featureGate.check(user.getId(), Capability.ATTACHMENTS);
        TaskManagerProperties.Plan plan = featureGate.planFor(user.getId());
        long maxBytes = (long) plan.getMaxAttachmentMB() * BYTES_PER_MB;
        if (request.sizeBytes() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "El plan permite archivos de hasta " + plan.getMaxAttachmentMB() + " MB. Mejora tu plan para adjuntar archivos más grandes.");
        }
        long count = attachments.countActiveByTaskId(taskId, LocalDateTime.now());
        if (count >= plan.getMaxAttachmentsPerTask()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Esta tarea alcanzó el máximo de adjuntos de tu plan. Mejora tu plan para añadir más.");
        }

        String filename = sanitizeFilename(request.filename());
        String contentType = request.contentType().trim();
        try {
            MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de contenido no válido");
        }
        TaskAttachment attachment = attachments.save(TaskAttachment.builder().task(task).user(user)
                .filename(filename).contentType(contentType).sizeBytes(request.sizeBytes())
                .storageKey(UUID.randomUUID().toString())
                .uploadExpiresAt(LocalDateTime.now().plusMinutes(5)).build());
        return toResponse(attachment, maxBytes);
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> list(UUID taskId, String email) {
        User user = currentUser(email);
        requireTaskAccess(taskId, user, false);
        return attachments.findUploadedByTaskId(taskId).stream().map(a -> toResponse(a, null)).toList();
    }

    @Transactional
    public void uploadLocal(UUID taskId, UUID attachmentId, String email, HttpServletRequest request) throws IOException {
        if (!properties.getBilling().isSimulate()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        User user = currentUser(email);
        requireTaskAccess(taskId, user, false);
        TaskAttachment attachment = requireAttachment(taskId, attachmentId);
        requireUploader(attachment, user);
        requireUploadIsOpen(attachment);
        if (request.getContentLengthLong() >= 0 && request.getContentLengthLong() != attachment.getSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño del archivo no coincide con el declarado");
        }
        storage.storeLocal(attachment, request.getInputStream());
        attachment.setUploaded(true);
        attachments.save(attachment);
    }

    @Transactional
    public void completeS3Upload(UUID taskId, UUID attachmentId, String email) throws IOException {
        if (properties.getBilling().isSimulate()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        User user = currentUser(email);
        requireTaskAccess(taskId, user, false);
        TaskAttachment attachment = attachments.findByIdForUpdate(attachmentId)
                .filter(a -> a.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        requireUploader(attachment, user);
        requireUploadIsOpen(attachment);
        if (storage.remoteObjectSize(attachment) != attachment.getSizeBytes()) {
            storage.delete(attachment);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontró un archivo válido en S3");
        }
        attachment.setUploaded(true);
        attachments.save(attachment);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadLocal(UUID taskId, UUID attachmentId, String email) {
        if (!properties.getBilling().isSimulate()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        User user = currentUser(email);
        requireTaskAccess(taskId, user, false);
        TaskAttachment attachment = requireAttachment(taskId, attachmentId);
        if (!attachment.isUploaded()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no disponible");
        FileSystemResource resource = new FileSystemResource(storage.localPath(attachment));
        if (!resource.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no disponible");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeHeaderFilename(attachment.getFilename()) + "\"")
                .header("X-Content-Type-Options", "nosniff").body(resource);
    }

    @Transactional
    public void delete(UUID taskId, UUID attachmentId, String email) throws IOException {
        User user = currentUser(email);
        Task task = requireTaskAccess(taskId, user, false);
        TaskAttachment attachment = requireAttachment(taskId, attachmentId);
        boolean admin = RoleNames.normalize(user.getRole()).equals("ADMIN");
        boolean projectOwner = task.getProject() != null && task.getProject().getOwner().getId().equals(user.getId());
        if (!attachment.getUser().getId().equals(user.getId()) && !admin && !projectOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien subió el adjunto, el propietario o un administrador puede eliminarlo");
        }
        storage.delete(attachment);
        attachments.delete(attachment);
    }

    private Task requireTaskAccess(UUID taskId, User actor, boolean lock) {
        Task task = (lock ? tasks.findByIdForUpdate(taskId) : tasks.findById(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada"));
        if (task.getProject() != null && !RoleNames.normalize(actor.getRole()).equals("ADMIN")
                && !task.getProject().getOwner().getId().equals(actor.getId())
                && !members.existsByProject_IdAndUser_Id(task.getProject().getId(), actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debes pertenecer al proyecto para acceder a sus adjuntos");
        }
        return task;
    }

    private TaskAttachment requireAttachment(UUID taskId, UUID attachmentId) {
        return attachments.findByIdAndTask_Id(attachmentId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
    }

    private void requireUploader(TaskAttachment attachment, User actor) {
        if (!attachment.getUser().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien inició la carga puede completarla");
        }
    }

    private void requireUploadIsOpen(TaskAttachment attachment) {
        if (attachment.isUploaded() || attachment.getUploadExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "La carga expiró o ya fue completada");
        }
    }

    private User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String sanitizeFilename(String name) {
        String filename = name.trim().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo no válido");
        }
        return filename;
    }

    private String safeHeaderFilename(String filename) {
        return filename.replaceAll("[\"\\r\\n]", "_");
    }

    private TaskAttachmentResponse toResponse(TaskAttachment attachment, Long maxBytes) {
        TaskAttachmentStorageService.UploadTarget upload = maxBytes == null ? null : storage.uploadTarget(attachment, maxBytes);
        return new TaskAttachmentResponse(attachment.getId(), attachment.getFilename(), attachment.getContentType(),
                attachment.getSizeBytes(), storage.downloadUrl(attachment), upload == null ? null : upload.url(),
                upload == null ? null : upload.fields(), attachment.getCreatedAt());
    }
}
