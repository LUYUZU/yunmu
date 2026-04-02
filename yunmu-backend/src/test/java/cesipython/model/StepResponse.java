package cesipython.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class StepResponse {
    private boolean success;

    @JsonProperty("animal_id")
    private String animalId;

    private int steps;

    @JsonProperty("step_frequency")
    private double stepFrequency;

    @JsonProperty("walking_distance")
    private double walkingDistance;

    @JsonProperty("activity_level")
    private String activityLevel;

    @JsonProperty("daily_summary")
    private Map<String, Object> dailySummary;

    private Long timestamp;
    private String error;
}
