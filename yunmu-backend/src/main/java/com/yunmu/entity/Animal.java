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

    /**
     * 项圈设备编号（device_code）：一个项圈同时只能绑定一只动物，唯一约束保证。
     * 项圈可换绑：同一项圈既可用于牛也可用于羊；未绑定时为 NULL。
     */
    @Column(name = "device_code", unique = true)
    private String deviceCode;

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
