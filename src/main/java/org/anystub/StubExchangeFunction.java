package org.anystub;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeFunctions;
import reactor.core.publisher.Mono;

public class StubExchangeFunction implements ExchangeFunction {
    private static final boolean reactorClientPresent;
    private static final boolean jettyClientPresent;
    private static final boolean httpComponentsClientPresent;
    final ExchangeFunction real = ExchangeFunctions.create(new StubClientHttpConnector(initConnector()));

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
        return real.exchange(request);
    }
    private ClientHttpConnector initConnector() {
        if (reactorClientPresent) {
            return new ReactorClientHttpConnector();
        } else if (jettyClientPresent) {
            return new JettyClientHttpConnector();
        } else if (httpComponentsClientPresent) {
            return new HttpComponentsClientHttpConnector();
        } else {
            throw new IllegalStateException("No suitable default ClientHttpConnector found");
        }
    }

    static {
        ClassLoader loader = StubExchangeFunction.class.getClassLoader();
        reactorClientPresent = false;//ClassUtils.isPresent("reactor.netty.http.client.HttpClient", loader);
        jettyClientPresent = false;//ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient", loader);
        httpComponentsClientPresent = ClassUtils.isPresent("org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient", loader) && ClassUtils.isPresent("org.apache.hc.core5.reactive.ReactiveDataConsumer", loader);
    }
}
