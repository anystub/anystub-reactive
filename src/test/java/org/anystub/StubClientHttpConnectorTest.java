package org.anystub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.anystub.http.AnySettingsHttp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

class StubClientHttpConnectorTest {

    static class Success {
        public int total;
    }
    static class Quote{
        public String quote;
    }
    static class Contents {
        public Quote[] quotes;
    }
    static class QuoteResp {
        public Success success;
        public Contents contents;
    }

    @Test
    @AnySettingsHttp(allHeaders = true, bodyTrigger = "http")
    @AnyStubId()
    void TestGetRequest() throws JsonProcessingException {
        WebClient build = WebClient.builder()
                .exchangeFunction(new StubExchangeFunction())
                .baseUrl("https://quotes.rest")
                .build();

        ResponseEntity<Flux<DataBuffer>> result = build.get()
                .uri("qod")
                .attribute("language", "en")
                .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                .retrieve()
                .toEntityFlux(DataBuffer.class)
                .block();

        Assertions.assertEquals(200, result.getStatusCodeValue());
        Assertions.assertEquals("application/json; charset=utf-8", result.getHeaders().getFirst("Content-Type"));

        Flux<DataBuffer> body = result.getBody();

        String collect = body.map(d -> d.toString(StandardCharsets.UTF_8))
                .collectList()
                .block().
                stream().collect(Collectors.joining());


        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        QuoteResp quotes = objectMapper.readValue(collect, QuoteResp.class);
        Assertions.assertEquals(1, quotes.success.total);
        Assertions.assertEquals("Regardless", quotes.contents.quotes[0].quote.substring(0,10));

    }
}