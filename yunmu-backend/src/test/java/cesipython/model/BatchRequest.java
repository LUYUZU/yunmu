package cesipython.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class BatchRequest {
    @JsonProperty("data")
    private List<AnimalSimpleData> data;

    @Data
    public static class AnimalSimpleData {
        @JsonProperty("animal_id")
        private String animalId;

        @JsonProperty("accel_x")
        private double accelX;

        @JsonProperty("accel_y")
        private double accelY;

        @JsonProperty("accel_z")
        private double accelZ;
    }
}
