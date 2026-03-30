// Animal.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "animals")
@Data
public class Animal {
    @Id
    private String animalId;

    @Column(nullable = false)
    private String animalType; // cow, sheep

    @Column(nullable = false)
    private String breed;

    private Integer age;
    private Double weight;

    @Column(name = "registration_date")
    private LocalDateTime registrationDate;

    private String ownerId;
    private String pastureId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
