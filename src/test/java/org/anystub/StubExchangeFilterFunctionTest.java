package org.anystub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.anystub.mgmt.BaseManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpPort = 8080)
class StubExchangeFilterFunctionTest {
    private WebClient webClient;

    @BeforeEach
    void setup() {
        webClient = WebClient.builder()
                .filter(new StubExchangeFilterFunction())
                .build();
    }


    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    void testEFFGetRequest(WireMockRuntimeInfo wmRuntimeInfo) throws JsonProcessingException {

        // The static DSL will be automatically configured for you
        stubFor(WireMock.get("/").willReturn(ok()
                .withBody("{\"test\":\"ok\"}")));


        // Info such as port numbers is also available
        int port = wmRuntimeInfo.getHttpPort();
        String block =
                webClient.get()
                        .uri("http://localhost:"+port)
                        .retrieve()
                        .toEntityFlux(String.class)
                        .block().getBody().collectList().block()
                        .stream().collect(Collectors.joining());

        Assertions.assertEquals("{\"test\":\"ok\"}", block);

        long times = BaseManagerFactory.locate()
                .times();
        Assertions.assertEquals(1, times);

    }


    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    @AnySettingsHttp(headers = "Accept")
    void testEffSavingHeaders(WireMockRuntimeInfo wmRuntimeInfo) {
        // The static DSL will be automatically configured for you
        stubFor(WireMock.get("/").willReturn(ok()
                .withHeader("x-forward", "test")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"test\":\"ok\"}")));


        // Info such as port numbers is also available
        int port = wmRuntimeInfo.getHttpPort();
        String block =
                webClient.get()
                        .uri("http://localhost:"+port)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("Accept", "application/x-ndjson", "plain/text", MediaType.ALL_VALUE)
                        .retrieve()
                        .toEntityFlux(String.class)
                        .block().getBody().collectList().block()
                        .stream().collect(Collectors.joining());

        Assertions.assertEquals("{\"test\":\"ok\"}", block);

        long times = BaseManagerFactory.locate()
                .times();
        Assertions.assertEquals(1, times);

        Document document = BaseManagerFactory.locate()
                .history()
                .findFirst().get();

        Assertions.assertTrue(document.matchEx_to(null, null, "Accept:.*"));

        ArrayList<String> objects = new ArrayList<>();
        document.getVals().forEach(objects::add);

        String s1;
        s1 = objects.stream().filter(s -> s.startsWith("Content-Type"))
                .findFirst().get();
        Assertions.assertEquals("Content-Type: application/json", s1);

        s1 = objects.stream().filter(s -> s.startsWith("x-forward"))
                .findFirst().get();
        Assertions.assertEquals("x-forward: test", s1);

    }


    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    @AnySettingsHttp(headers = "Accept", bodyTrigger = "")
    void testEffSavingRequestBody(WireMockRuntimeInfo wmRuntimeInfo) {
        // The static DSL will be automatically configured for you
        stubFor(WireMock.post("/")
                .willReturn(ok()
                        .withHeader("x-forward", "test")
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"test\":\"ok\"}")));


        StubClientHttpConnector2Test.Request1 request1 = new StubClientHttpConnector2Test.Request1();
        request1.code =23;
        request1.msg = "test msg";
        request1.date = LocalDate.of(2022,6,30);


        // Info such as port numbers is also available
        int port = wmRuntimeInfo.getHttpPort();
        String block =
                webClient.post()
                        .uri("http://localhost:"+port)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("Accept", "application/x-ndjson", "plain/text", MediaType.ALL_VALUE)
                        .bodyValue(request1)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

        Assertions.assertEquals("{\"test\":\"ok\"}", block);

        long times = BaseManagerFactory.locate()
                .times();
        Assertions.assertEquals(1, times);

        Document document = BaseManagerFactory.locate()
                .history()
                .findFirst().get();

        Assertions.assertTrue(document.matchEx_to(null, null,
                "Accept:.*", null,
                ".*test msg.*"));


    }
}