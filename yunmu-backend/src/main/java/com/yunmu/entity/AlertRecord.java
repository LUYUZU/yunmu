// AlertRecord.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "alert_records")
public class AlertRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "alert_time", nullable = false)
    private LocalDateTime alertTime;

    @Column(name = "alert_type", nullable = false)
    private String alertType; // HEALTH, BEHAVIOR, LOCATION

    @Column(name = "alert_subtype")
    private String alertSubtype; // FEVER, HEART_RATE, RUMINATION_ABNORMAL, OUT_OF_BOUNDS

    @Column(name = "alert_level", nullable = false)
    private String alertLevel; // INFO, WARNING, CRITICAL

    @Column(name = "alert_message", length = 500)
    private String alertMessage;

    @Column(name = "alert_data", columnDefinition = "TEXT")
    private String alertData;

    @Column(name = "alert_status", nullable = false)
    private String alertStatus; // ACTIVE, ACKNOWLEDGED, RESOLVED

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_time")
    private LocalDateTime acknowledgedTime;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "notification_channels")
    private String notificationChannels; // WEB, SMS, EMAIL, APP

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
