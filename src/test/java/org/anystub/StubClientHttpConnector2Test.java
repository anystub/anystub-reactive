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
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

@WireMockTest(httpPort = 8080)
class StubClientHttpConnector2Test {
    private WebClient webClient;

    @BeforeEach
    void setup() {
        webClient = WebClient.builder()
                .exchangeFunction(new StubExchangeFunction())
                .build();
    }

    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    void TestGetRequest2(WireMockRuntimeInfo wmRuntimeInfo) throws JsonProcessingException {

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
    void testSavingHeaders(WireMockRuntimeInfo wmRuntimeInfo) {
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

    }


    static class Request1 {
        int code;
        String msg;
        LocalDate date;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }
    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    @AnySettingsHttp(headers = "Accept", bodyTrigger = "")
    void testSavingRequestBody(WireMockRuntimeInfo wmRuntimeInfo) {
        // The static DSL will be automatically configured for you
        stubFor(WireMock.post("/")
                .willReturn(ok()
                .withHeader("x-forward", "test")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"test\":\"ok\"}")));


        Request1 request1 = new Request1();
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

//        Assertions.assertTrue(document.matchEx_to(null, null, "Accept:.*"));

    }

    @Test
    @AnyStubId(requestMode = RequestMode.rmNone)
    @AnySettingsHttp(headers = "Accept", bodyTrigger = "")
    void testUseSaved(WireMockRuntimeInfo wmRuntimeInfo) {
        // The static DSL will be automatically configured for you
        stubFor(WireMock.post("/")
                .willReturn(ok()
                        .withHeader("x-forward", "test")
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"test\":\"ok\"}")));


        Request1 request1 = new Request1();
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

    }


    @Test
    @AnyStubId(requestMode = RequestMode.rmAll)
    @AnySettingsHttp(bodyTrigger = "", bodyMask = {"secret", "password", ": ....-.* ", "\\d{4},\\d{1,2},\\d{1,2}"})
    void testMaskRequest(WireMockRuntimeInfo wmRuntimeInfo) {
        // The static DSL will be automatically configured for you
        stubFor(WireMock.post("/")
                .willReturn(ok()
                        .withHeader("x-forward", "test")
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"test\":\"ok\"}")));


        Request1 request1 = new Request1();
        request1.code =23;
        request1.msg = String.format("hypothetical request containing a secret data like a password, "+
                        "or a variable timestamp: %s in the middle of request",
                LocalDateTime.now().toString());
        request1.date = LocalDate.now();


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

        Assertions.assertTrue(document.key_to_string().contains("containing a ... data like a ..., or a variable timestamp...request"));
        Assertions.assertTrue(document.key_to_string().contains("date\":[...]"), document.key_to_string());
    }

    @Test
    void testResponseBody() {

    }

    @Test
    void testResponseHeaders() {

    }

    @Test
    void testBase64RequestResponse(){

    }




}