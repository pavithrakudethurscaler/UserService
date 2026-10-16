package com.ecom.users.repos;

import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepo extends JpaRepository<Token, Long> {
    Optional<Token> findByValueAndDeletedAndExpiryAtGreaterThan(String value, boolean deleted, long now);

    Optional<Token> findByValueAndDeleted(String value, boolean deleted);

    List<Token> findByUserAndDeleted(User user, boolean deleted);
}
