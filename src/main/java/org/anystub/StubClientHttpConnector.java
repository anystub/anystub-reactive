package org.anystub;

import org.anystub.http.AnySettingsHttp;
import org.anystub.http.AnySettingsHttpExtractor;
import org.anystub.http.HttpUtil;
import org.anystub.http.StubHttpClient;
import org.anystub.mgmt.BaseManagerFactory;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;


public class StubClientHttpConnector implements ClientHttpConnector {

    private static final String HEADER_MASK = "^[A-Za-z0-9\\-]+: .+";

    final ClientHttpConnector real;
    private Base fallbackBase = null;

    public StubClientHttpConnector(ClientHttpConnector real) {
        this.real = real;
    }


    public static List<String> filterHeaders(HttpHeaders headers){
        boolean currentAllHeaders = HttpUtil.globalAllHeaders;
        AnySettingsHttp settings = AnySettingsHttpExtractor.discoverSettings();
        if (settings != null) {
            currentAllHeaders = settings.allHeaders();
        }

        Set<String> headersToAdd = new TreeSet<>();
        if(currentAllHeaders) {
            headersToAdd.addAll(headers.keySet());
        } else {
            if (settings != null) {
                headersToAdd.addAll(asList(settings.headers()));
            }
            if ((settings == null || !settings.overrideGlobal()) && HttpUtil.globalHeaders != null) {
                headersToAdd.addAll(asList(HttpUtil.globalHeaders));
            }
        }


        return headersToAdd.stream()
                .map(h->headerToString(headers, h))
                .sorted()
                .collect(Collectors.toList());
    }

    public static String headerToString(HttpHeaders headers, String h) {
        return String.format("%s: %s", h, String.join(", ", headers.getOrEmpty(h)));
    }


    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

        MockClientHttpRequest mockClientHttpRequest = new MockClientHttpRequest(method, uri);
//        BasicHttpRequest mockClientHttpRequest = new BasicHttpRequest(method.name(), uri);
        requestCallback.apply(mockClientHttpRequest).block();
        ArrayList<String> key = new ArrayList<>();
        key.add(method.name());
        key.add("HTTP/1.1");
        key.addAll(filterHeaders(mockClientHttpRequest.getHeaders()));
        key.add(uri.toString());

        // @todo add request body if required

//        if (true) {
//            System.out.printf("%s", key);
//            throw new UnsupportedOperationException();
//        }
//        HttpComponentsClientHttpConnector
        return getBase()
                .request2(
                new Supplier<Mono<ClientHttpResponse>, RuntimeException>() {
                    @Override
                    public Mono<ClientHttpResponse> get()  {
                        return real.connect(method, uri, requestCallback);
                    }
                },
                new Decoder<Mono<ClientHttpResponse>>() {
                    @Override
                    public Mono<ClientHttpResponse> decode(Iterable<String> iterable) {
                        Iterator<String> iterator = iterable.iterator();
                        String[] protocol = iterator.next().split("[/.]");
                        String code = iterator.next();
                        String reason = iterator.next();

                        MockClientHttpResponse clientResponse =
                                new MockClientHttpResponse(Integer.parseInt(code));

                        String postHeader = null;
                        while (iterator.hasNext()) {
                            String header;
                            header = iterator.next();
                            if (!header.matches(HEADER_MASK)) {
                                postHeader = header;
                                break;
                            }

                            int i = header.indexOf(": ");
                            clientResponse.getHeaders().set(header.substring(0, i), header.substring(i + 2));
                        }
//
//                        clientResponse.getCookies().putAll(response.getCookies());

                        if (postHeader != null) {
                            clientResponse.setBody(postHeader);
                        }
                        return Mono.just(clientResponse);
                    }
                },
                new Encoder<Mono<ClientHttpResponse>>() {
                    @Override
                    public Iterable<String> encode(Mono<ClientHttpResponse> clientHttpResponseMono) {
                        List<String> res = new ArrayList<>();
                        ClientHttpResponse response = clientHttpResponseMono.block();
                        if (response == null) {
                            return res;
                        }

                        res.add("HTTP/1.1");
                        res.add(Integer.toString(response.getRawStatusCode()));
                        res.add(response.getStatusCode().getReasonPhrase());

                        res.addAll(response.getHeaders()
                                .keySet()
                                .stream()
                                .map(h->headerToString(response.getHeaders(), h))
                                .sorted()
                                .collect(Collectors.toList()));

                        Flux<DataBuffer> body = response.getBody();

                        String collect = String.join("", body.map(d -> d.toString(StandardCharsets.UTF_8))
                                .collectList()
                                .block());

                        res.add(collect);

                        return res;
                    }
                },
                key.toArray(new String[0])
        );



    }


    private Base getBase() {
        AnyStubId s = AnyStubFileLocator.discoverFile();
        if (s != null) {
            return BaseManagerFactory
                    .getBaseManager()
                    .getBase(s.filename())
                    .constrain(s.requestMode());
        }
        if (fallbackBase != null) {
            return fallbackBase;
        }
        return BaseManagerFactory
                .getBaseManager()
                .getBase();
    }
    public StubClientHttpConnector setFallbackBase(Base base) {
        this.fallbackBase = base;
        return this;
    }
}
