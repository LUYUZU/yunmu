package cesipython.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PostureRequest {
    @JsonProperty("animal_id")
    private String animalId;

    @JsonProperty("accel_x")
    private double accelX;

    @JsonProperty("accel_y")
    private double accelY;

    @JsonProperty("accel_z")
    private double accelZ;

    @JsonProperty("gyro_x")
    private double gyroX;

    @JsonProperty("gyro_y")
    private double gyroY;

    @JsonProperty("gyro_z")
    private double gyroZ;

    @JsonProperty("timestamp")
    private long timestamp;

    public PostureRequest() {
        this.timestamp = System.currentTimeMillis() / 1000;
    }

    public PostureRequest(String animalId, double accelX, double accelY, double accelZ) {
        this();
        this.animalId = animalId;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.gyroX = 0;
        this.gyroY = 0;
        this.gyroZ = 0;
    }
}