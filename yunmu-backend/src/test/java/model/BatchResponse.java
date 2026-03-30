package model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class BatchResponse {
    private boolean success;
    private int count;
    private List<BatchItem> data;
    private String error;

    @Data
    public static class BatchItem {
        @JsonProperty("animal_id")
        private String animalId;

        private String behavior;
        private double confidence;
        private String posture;

        @JsonProperty("posture_confidence")
        private double postureConfidence;
    }
}