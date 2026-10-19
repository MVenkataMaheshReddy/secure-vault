package com.securevault.repository;

import com.securevault.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByName(String name);

    Optional<User> findByEmailOrName(String email, String name);

    boolean existsByEmail(String email);

    boolean existsByName(String name);
}
