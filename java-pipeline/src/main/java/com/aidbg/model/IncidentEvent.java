package com.aidbg.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEvent {
    @JsonProperty("sysId")       private String sysId;
    @JsonProperty("number")      private String number;
    @JsonProperty("shortDescription") private String shortDescription;
    @JsonProperty("priority")    private String priority;
    @JsonProperty("state")       private String state;
    @JsonProperty("assignedTo")  private String assignedTo;
    @JsonProperty("cmdbCi")      private String cmdbCi;
    @JsonProperty("updatedAt")   private String updatedAt;
}
