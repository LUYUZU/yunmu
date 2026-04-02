package cesipython.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class BehaviorRequest {
    @JsonProperty("animal_id")
    private String animalId;

    @JsonProperty("accel_data")
    private List<Double> accelData;

    @JsonProperty("sound_data")
    private List<Double> soundData;

    @JsonProperty("accel_sampling_rate")
    private int accelSamplingRate = 50;

    @JsonProperty("sound_frequency")
    private int soundFrequency = 1000;

    @JsonProperty("timestamp")
    private long timestamp;

    public BehaviorRequest() {
        this.timestamp = System.currentTimeMillis() / 1000;
    }
}
