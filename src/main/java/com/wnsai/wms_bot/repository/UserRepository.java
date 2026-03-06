package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByRefreshToken(String refreshToken);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.refreshToken = :token WHERE u.id = :id")
    void updateRefreshToken(@Param("id") UUID id, @Param("token") String token);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.id = :id")
    void updateLastLogin(@Param("id") UUID id, @Param("loginTime") OffsetDateTime loginTime);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.passwordHash = :hash WHERE u.id = :id")
    void updatePassword(@Param("id") UUID id, @Param("hash") String hash);
}
