package org.anystub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.anystub.mgmt.BaseManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

@WireMockTest(httpPort = 8080)
class StubClientHttpConnector2Test {
    @Test
//    @AnySettingsHttp(allHeaders = true, bodyTrigger = "http")
//    @AnyStubId(requestMode = RequestMode.rmAll)
    @AnyStubId()
    void TestGetRequest() {

    }

    @Test
    @AnyStubId()
    void TestGetRequest2(WireMockRuntimeInfo wmRuntimeInfo) throws JsonProcessingException {

        // The static DSL will be automatically configured for you
        stubFor(WireMock.get("/").willReturn(
                ok()
                        .withBody("{\"test\":\"ok\"}")));


        // Info such as port numbers is also available
        int port = wmRuntimeInfo.getHttpPort();

        WebClient webClient = WebClient.builder()
                .exchangeFunction(new StubExchangeFunction())
                .build();

        String block =
                webClient.get()
                .uri("http://localhost:8080")
                .retrieve()
                .toEntityFlux(String.class)
                .block().getBody().collectList().block()
                        .stream().collect(Collectors.joining());

        Assertions.assertEquals("{\"test\":\"ok\"}", block);

        long times = BaseManagerFactory.locate()
                .times();
        Assertions.assertEquals(1, times);

    }
}