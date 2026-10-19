package com.securevault.entity;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "revoked_token")
public class RevokedToken {

    @Id
    @Column(name = "token", length = 500)
    private String token;

    @Column(name = "revoked_at")
    private Date revokedAt;

    public RevokedToken() {}

    public RevokedToken(String token) {
        this.token = token;
        this.revokedAt = new Date();
    }

    public RevokedToken(String token, Date revokedAt) {
        this.token = token;
        this.revokedAt = revokedAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Date getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Date revokedAt) {
        this.revokedAt = revokedAt;
    }
}
