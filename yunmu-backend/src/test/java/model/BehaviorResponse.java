package model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class BehaviorResponse {
    private boolean success;

    @JsonProperty("animal_id")
    private String animalId;

    private String behavior;
    private double confidence;
    private List<Double> features;
    private Long timestamp;
    private String error;
}

