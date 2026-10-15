package com.taskmanager.controller;

import com.taskmanager.dto.request.AdminUserUpdateRequest;
import com.taskmanager.dto.request.ForgotPasswordRequest;
import com.taskmanager.dto.request.LoginRequest;
import com.taskmanager.dto.request.RegisterRequest;
import com.taskmanager.dto.request.ProfileUpdateRequest;
import com.taskmanager.dto.request.ResetPasswordRequest;
import com.taskmanager.dto.response.AdminUserResponse;
import com.taskmanager.dto.response.JwtResponse;
import com.taskmanager.dto.response.MessageResponse;
import com.taskmanager.dto.response.ProfileResponse;
import com.taskmanager.entity.PasswordResetToken;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PasswordResetTokenRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.JwtTokenProvider;
import com.taskmanager.security.UserDetailsServiceImpl;
import com.taskmanager.security.RoleNames;
import com.taskmanager.security.EmailAddresses;
import com.taskmanager.service.EmailService;
import com.taskmanager.service.NotificationEventService;
import com.taskmanager.service.SubscriptionService;
import com.taskmanager.entity.SubscriptionTier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "MANAGER", "SCRUM", "DEVELOPER");
    private static final int MAX_AVATAR_BYTES = 512 * 1024;
    private static final Pattern AVATAR_DATA_URI = Pattern.compile("^data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$");
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final NotificationEventService notifications;
    private final SubscriptionService subscriptions;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        String email = EmailAddresses.normalize(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) return ResponseEntity.badRequest().body("Error: El email ya está en uso");
        User user = User.builder().fullName(request.getFullName()).email(email)
                .password(passwordEncoder.encode(request.getPassword())).role(RoleNames.normalize("DEVELOPER")).build();
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("Usuario registrado exitosamente");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest request) {
        String email = EmailAddresses.normalize(request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return ResponseEntity.ok(new JwtResponse(accessToken, refreshToken, user.getEmail(), RoleNames.normalize(user.getRole()), user.getId(), user.getFullName()));
    }

    @GetMapping("/me")
    public ProfileResponse getCurrentProfile(Authentication authentication) {
        User user = currentUser(authentication);
        return ProfileResponse.from(user, subscriptions.resolveTier(user.getId()));
    }

    @PatchMapping("/me")
    public ProfileResponse updateCurrentProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                                Authentication authentication) {
        User user = currentUser(authentication);
        SubscriptionTier tier = subscriptions.resolveTier(user.getId());
        boolean premiumFields = request.avatarData() != null || request.timezone() != null || request.weekStartsOn() != null;
        boolean plusFields = request.jobTitle() != null || request.bio() != null || request.accentColor() != null;
        if (premiumFields && tier == SubscriptionTier.FREE) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "La foto y las preferencias de perfil requieren Premium.");
        }
        if (plusFields && tier != SubscriptionTier.PREMIUM_PLUS) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Los campos avanzados de perfil requieren Premium Plus.");
        }
        user.setFullName(request.fullName().trim());
        if (request.avatarData() != null) user.setAvatarData(validateAvatar(request.avatarData()));
        if (request.timezone() != null) user.setProfileTimezone(validateTimezone(request.timezone()));
        if (request.weekStartsOn() != null) {
            if (request.weekStartsOn() != 0 && request.weekStartsOn() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El inicio de semana debe ser domingo o lunes.");
            }
            user.setWeekStartsOn(request.weekStartsOn());
        }
        if (request.jobTitle() != null) user.setProfileJobTitle(emptyToNull(request.jobTitle()));
        if (request.bio() != null) user.setProfileBio(emptyToNull(request.bio()));
        if (request.accentColor() != null) user.setProfileAccentColor(emptyToNull(request.accentColor()));
        return ProfileResponse.from(userRepository.save(user), tier);
    }

    private String validateAvatar(String dataUri) {
        if (dataUri.isBlank()) return null;
        Matcher matcher = AVATAR_DATA_URI.matcher(dataUri);
        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen PNG, JPEG o WebP.");
        }
        try {
            byte[] image = Base64.getDecoder().decode(matcher.group(2));
            if (image.length == 0 || image.length > MAX_AVATAR_BYTES || !matchesImageSignature(matcher.group(1), image)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen no es válida o supera 512 KB.");
            }
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen codificada no es válida.");
        }
        return dataUri;
    }

    private boolean matchesImageSignature(String type, byte[] image) {
        return switch (type) {
            case "png" -> image.length >= 8 && (image[0] & 0xff) == 0x89 && image[1] == 0x50
                    && image[2] == 0x4e && image[3] == 0x47 && image[4] == 0x0d && image[5] == 0x0a
                    && image[6] == 0x1a && image[7] == 0x0a;
            case "jpeg" -> image.length >= 3 && (image[0] & 0xff) == 0xff && (image[1] & 0xff) == 0xd8
                    && (image[2] & 0xff) == 0xff;
            case "webp" -> image.length >= 12 && image[0] == 'R' && image[1] == 'I' && image[2] == 'F'
                    && image[3] == 'F' && image[8] == 'W' && image[9] == 'E' && image[10] == 'B' && image[11] == 'P';
            default -> false;
        };
    }

    private String validateTimezone(String timezone) {
        if (timezone.isBlank()) return null;
        String value = timezone.trim();
        try {
            ZoneId.of(value);
            return value;
        } catch (java.time.DateTimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La zona horaria no es válida.");
        }
    }

    private String emptyToNull(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private User currentUser(Authentication authentication) {
        String email = EmailAddresses.normalize(authentication.getName());
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String email = EmailAddresses.normalize(request.getEmail());
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            passwordResetTokenRepository.findByUserIdAndUsedFalse(user.getId())
                    .forEach(t -> { t.setUsed(true); passwordResetTokenRepository.save(t); });
            PasswordResetToken token = passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build());
            emailService.sendPasswordResetEmail(user.getEmail(), token.getToken());
        });
        return ResponseEntity.ok(new MessageResponse(
                "Si el email está registrado, recibirás instrucciones para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido"));
        if (token.isUsed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El token ya fue utilizado");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El token ha expirado");
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        return ResponseEntity.ok(new MessageResponse("Contraseña actualizada exitosamente"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)
                || !tokenProvider.isRefreshToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token inválido o expirado");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(tokenProvider.getUsernameFromToken(refreshToken));
        if (!userDetails.isEnabled()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("La cuenta está desactivada");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return ResponseEntity.ok(Map.of("accessToken", tokenProvider.generateAccessToken(authentication)));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<AdminUserResponse> getUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAdminResponse).toList();
    }

    @PostMapping("/admin/create-user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUserByAdmin(@Valid @RequestBody RegisterRequest request,
                                               @RequestParam String role) {
        String email = EmailAddresses.normalize(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) return ResponseEntity.badRequest().body("Error: El email ya está en uso");
        String normalizedRole = RoleNames.normalize(role);
        if (!ALLOWED_ROLES.contains(normalizedRole)) return ResponseEntity.badRequest().body("Error: El rol no es válido");
        User user = userRepository.save(User.builder().fullName(request.getFullName()).email(email)
                .password(passwordEncoder.encode(request.getPassword())).role(normalizedRole).build());
        notifications.notifyUser(user, "Cuenta creada", "Tu cuenta está lista para usar TaskFront.", "/dashboard/kanban");
        return ResponseEntity.status(HttpStatus.CREATED).body(toAdminResponse(user));
    }

    @PatchMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRequest request,
                                        Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        String role = RoleNames.normalize(request.getRole());
        if (!ALLOWED_ROLES.contains(role)) return ResponseEntity.badRequest().body("Rol no válido");
        User actor = userRepository.findByEmailIgnoreCase(EmailAddresses.normalize(authentication.getName())).orElseThrow();
        if (actor.getId().equals(user.getId()) && !request.getActive()) {
            return ResponseEntity.badRequest().body("No puedes desactivar tu propia cuenta");
        }
        boolean losingAdmin = RoleNames.normalize(user.getRole()).equals("ADMIN")
                && (!role.equals("ADMIN") || !request.getActive());
        if (losingAdmin && userRepository.countByRoleIgnoreCaseAndActiveTrue("admin") <= 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Debe permanecer al menos un administrador activo");
        }
        user.setRole(role);
        user.setActive(request.getActive());
        User saved = userRepository.save(user);
        if (saved.isActive()) notifications.notifyUser(saved, "Cuenta actualizada",
                "Tu rol o acceso fue actualizado por administración.", "/dashboard/kanban");
        return ResponseEntity.ok(toAdminResponse(saved));
    }

    private AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(),
                RoleNames.normalize(user.getRole()), user.isActive(), user.getCreatedAt());
    }
}
