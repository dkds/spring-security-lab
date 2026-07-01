package com.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement
public class EntityDescriptor {
    @JsonProperty("IDPSSODescriptor")
    IDPSSODescriptor idpSSODescriptor;

    @Data
    public static class IDPSSODescriptor {
        @JsonProperty("SingleSignOnService")
        @JacksonXmlElementWrapper(useWrapping = false)
        List<SingleSignOnService> singleSignOnService;
    }

    @Data
    public static class SingleSignOnService {
        @JsonProperty("Binding")
        String binding;
        @JsonProperty("Location")
        String location;
    }
}
