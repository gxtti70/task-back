package com.taskmanager.repository;

import com.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    // Método clave para la autenticación y validación de tokens
    Optional<User> findByEmail(String email);
    
    // Validar si el correo ya existe en el registro del MVP
    boolean existsByEmail(String email);
}
