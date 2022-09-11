package org.anystub;

import org.anystub.mgmt.BaseManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.File;
import java.util.function.Function;

import static org.anystub.Util.anystubContext;

class UtilTest {

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