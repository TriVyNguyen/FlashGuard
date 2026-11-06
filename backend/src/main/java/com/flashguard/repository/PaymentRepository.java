package com.flashguard.repository;

import com.flashguard.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);
}
