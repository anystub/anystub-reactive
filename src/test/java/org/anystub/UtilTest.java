package org.anystub;

import org.anystub.mgmt.BaseManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.File;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.anystub.Util.anystubContext;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ALLOW;
import static org.springframework.http.HttpHeaders.REFERER;

class UtilTest {

    @Test
    @AnyStubId
    @AnySettingsHttp(allHeaders = true, headers = "Accept")
    void testHeaderFilterAll() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ACCEPT, "testVal");
        headers.set(ALLOW, "testVal");
        headers.set(REFERER, "testVal");

        List<String> strings = Util.filterHeaders(headers);
        Assertions.assertEquals(asList("Accept: testVal", "Allow: testVal", "Referer: testVal"), strings);

    }

   @Test
    @AnyStubId
    @AnySettingsHttp(headers = "Accept")
    void testHeaderFilterOne() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ACCEPT, "testVal");
        headers.set(ALLOW, "testVal");
        headers.set(REFERER, "testVal");

        List<String> strings = Util.filterHeaders(headers);
        Assertions.assertEquals(asList("Accept: testVal"), strings);

    }

    @Test
    @AnyStubId
    void reactiveContext() {
        Base stub = anystubContext().get(Base.class);
        Assertions.assertNotNull(stub);
        Assertions.assertEquals("reactiveContext.yml",
                new File(stub.getFilePath()).getName());

    }

    @Test
    @AnyStubId(filename = "customName")
    void reactiveOptions() {
        StepVerifier.create(Mono.just(1),
                Util.anystubOptions())
                .expectAccessibleContext()
                .matches(context -> context.hasKey(Base.class))
                .matches(context -> context.get(Base.class)
                        .getFilePath()
                        .endsWith("customName.yml"))
                .then()
                .expectNext(1)
                .verifyComplete();
    }




}