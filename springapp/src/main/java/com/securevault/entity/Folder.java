package com.securevault.entity;

import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "folder")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fol_id")
    private Long fol_id;

    @Column(name = "fol_name", nullable = false)
    private String fol_name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = true)
    private User owner;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Column(name = "description")
    private String description;

    public Folder() {}

    public Folder(String fol_name, User owner, LocalDate createdAt, String description) {
        this.fol_name = fol_name;
        this.owner = owner;
        this.createdAt = createdAt;
        this.description = description;
    }

    public Long getFol_id() {
        return fol_id;
    }

    public void setFol_id(Long fol_id) {
        this.fol_id = fol_id;
    }

    public String getFol_name() {
        return fol_name;
    }

    public void setFol_name(String fol_name) {
        this.fol_name = fol_name;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
