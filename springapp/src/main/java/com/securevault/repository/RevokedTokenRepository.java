package com.securevault.repository;

import com.securevault.entity.RevokedToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends CrudRepository<RevokedToken, String> {
    boolean existsByToken(String token);
}
