package com.taskmanager.repository;
import com.taskmanager.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    boolean existsByProviderPaymentId(String providerPaymentId);
}
