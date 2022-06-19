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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.anystub.Util.escapeCharacterString;
import static org.anystub.Util.toCharacterString;
import static org.anystub.http.HttpUtil.globalBodyMask;
import static org.anystub.http.HttpUtil.globalBodyTrigger;


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
//        BasicHttpRequest mockClientHttpRequest1 = new BasicHttpRequest(method.name(), uri);
        requestCallback.apply(mockClientHttpRequest).block();
        ArrayList<String> key = new ArrayList<>();
        key.add(method.name());
        key.add("HTTP/1.1");
        key.addAll(filterHeaders(mockClientHttpRequest.getHeaders()));
        key.add(uri.toString());


        if (matchBodyRule(uri.toString())) {
            String body = mockClientHttpRequest.getBodyAsString().blockOptional().orElse("");
            if (Util.isText(body)) {
                key.add(escapeCharacterString(body));
            } else {
                key.add(toCharacterString(body.getBytes(StandardCharsets.UTF_8)));
            }
        }

        return getBase()
                .request2(
                        () -> real.connect(method, uri, requestCallback),
                        (Iterable<String> iterable) -> {
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
                        },
                        (Mono<ClientHttpResponse> clientHttpResponseMono) -> {
                            List<String> res = new ArrayList<>();
                            ClientHttpResponse response = clientHttpResponseMono.block();
                            if (response == null) {
                                return res;
                            }

                            res.add("HTTP/1.1");
                            res.add(Integer.toString(response.getRawStatusCode()));
                            res.add(response.getStatusCode().getReasonPhrase());

                            List<String> headers = response.getHeaders()
                                    .keySet()
                                    .stream()
                                    .filter(HttpSettingsUtil.filterHeaders())
                                    .sorted(String::compareTo)
                                    .map(h -> headerToString(response.getHeaders(), h))
                                    .collect(Collectors.toList());

                            res.addAll(headers);

                            Flux<DataBuffer> body = response.getBody();

                            String collect = String.join("", body.map(d -> d.toString(StandardCharsets.UTF_8))
                                    .collectList()
                                    .blockOptional().orElse(List.of()));

                            res.add(collect);

                            return res;
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

    static boolean matchBodyRule(String url) {
        Set<String> currentBodyTriggers = new HashSet<>();

        AnySettingsHttp settings = AnySettingsHttpExtractor.discoverSettings();

        if (settings != null) {
            currentBodyTriggers.addAll(asList(settings.bodyTrigger()));
        }

        if ((settings == null || !settings.overrideGlobal()) && globalBodyTrigger != null) {
            currentBodyTriggers.addAll(asList(globalBodyTrigger));
        }


        return currentBodyTriggers.stream()
                .anyMatch(url::contains);
    }


    static String maskBody(String s) {
        Set<String> currentBodyMask = new HashSet<>();

        AnySettingsHttp settings = AnySettingsHttpExtractor.discoverSettings();
        if (settings != null) {
            currentBodyMask.addAll(asList(settings.bodyMask()));
        }

        if ((settings != null || !settings.overrideGlobal()) && globalBodyMask != null) {
            currentBodyMask.addAll(asList(globalBodyMask));
        }

        return currentBodyMask.stream()
                .reduce(s, (r, m) -> r.replaceAll(m, "..."));
    }
}
