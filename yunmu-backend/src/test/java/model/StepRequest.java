package model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class StepRequest {
    @JsonProperty("animal_id")
    private String animalId;

    @JsonProperty("accel_x")
    private List<Double> accelX;

    @JsonProperty("accel_y")
    private List<Double> accelY;

    @JsonProperty("accel_z")
    private List<Double> accelZ;

    @JsonProperty("timestamps")
    private List<Double> timestamps;

    @JsonProperty("posture")
    private String posture;

    @JsonProperty("timestamp")
    private long timestamp;

    public StepRequest() {
        this.timestamp = System.currentTimeMillis() / 1000;
        this.posture = "walking";
    }
}
