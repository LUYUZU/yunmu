package cesipython.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class PostureResponse {
    private boolean success;

    @JsonProperty("animal_id")
    private String animalId;

    @JsonProperty("posture_type")
    private String postureType;

    private double confidence;
    private Map<String, Object> features;
    private Long timestamp;
    private String error;
}
