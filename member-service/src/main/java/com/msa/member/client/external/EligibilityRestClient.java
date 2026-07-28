package com.msa.member.client.external;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EligibilityRestClient {
    private final RestClient restClient;

    public EligibilityRestClient(@Value("${app.external-eligibility-url:http://localhost:9090}") String externalEligibilityUrl) {
        this.restClient = RestClient.builder().baseUrl(externalEligibilityUrl).build();
    }

    public Map selectEligibility(String memberNo) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/eligibility").queryParam("memberNo", memberNo).build())
                .retrieve()
                .body(Map.class);
    }
}
