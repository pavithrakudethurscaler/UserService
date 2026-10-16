package com.ecom.users.repos;

import com.ecom.users.models.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByValueAndDeletedAndExpiryAtGreaterThan(String value, boolean deleted, long now);
}
