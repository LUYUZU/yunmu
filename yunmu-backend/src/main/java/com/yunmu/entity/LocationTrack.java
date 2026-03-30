// LocationTrack.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "location_tracks", indexes = {
        @Index(name = "idx_animal_time", columnList = "animalId, timestamp"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class LocationTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double altitude;
    private Double speed;
    private Double direction;

    @Column(name = "position_accuracy")
    private Double positionAccuracy;

    @Column(name = "satellite_count")
    private Integer satelliteCount;

    @Column(name = "is_valid_gps")
    private Boolean isValidGps = true;

    @Column(name = "gps_quality")
    private String gpsQuality; // GOOD, MEDIUM, POOR

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "signal_strength")
    private Integer signalStrength;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
