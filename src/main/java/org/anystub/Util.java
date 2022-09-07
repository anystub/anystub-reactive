package org.anystub;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.anystub.SettingsUtil.matchBodyRule;
import static org.anystub.StringUtil.addTextPrefix;
import static org.anystub.StringUtil.escapeCharacterString;
import static org.anystub.StringUtil.toCharacterString;

public class Util {
    public static final String HEADER_MASK = "^[A-Za-z0-9\\-]+: .+";

    private Util() {
    }

    /**
     * @param headers
     * @return
     */
    public static List<String> filterHeaders(HttpHeaders headers) {

        boolean currentAllHeaders = HttpGlobalSettings.globalAllHeaders;
        AnySettingsHttp settings = AnySettingsHttpExtractor.discoverSettings();
        if (settings != null) {
            currentAllHeaders = settings.allHeaders();
        }

        Set<String> headersToAdd = new TreeSet<>();
        if (currentAllHeaders) {
            headersToAdd.addAll(headers.keySet());
        } else {
            if (settings != null) {
                headersToAdd.addAll(asList(settings.headers()));
            }
            if ((settings == null || !settings.overrideGlobal()) && HttpGlobalSettings.globalHeaders != null) {
                headersToAdd.addAll(asList(HttpGlobalSettings.globalHeaders));
            }
        }


        return headersToAdd.stream()
                .map(h -> headerToString(headers, h))
                .sorted()
                .collect(Collectors.toList());
    }

    public static String headerToString(HttpHeaders headers, String h) {
        return String.format("%s: %s", h, String.join(", ", headers.getOrEmpty(h)));
    }


    public static Mono<String> extractStringMono(Flux<DataBuffer> body) {
        return body.map(dataBuffer ->
                        StringUtil.readStream(dataBuffer.asInputStream(true)))
                .collectList()
                .map(block -> {
                    if (block == null || block.isEmpty()) {
                        return new byte[0];
                    } else {
                        return
                                block.stream()
                                        .reduce(new byte[0], (result, bytes2) -> {
                                            int v = result.length;
                                            result = Arrays.copyOf(result, v + bytes2.length);
                                            System.arraycopy(bytes2, 0, result, v, bytes2.length);
                                            return result;
                                        });
                    }
                })
                .map(bodyContext -> {
                    String bodyString = toCharacterString(bodyContext);
                    if (bodyString.matches(HEADER_MASK)) {
                        bodyString = addTextPrefix(bodyString);
                    }
                    return bodyString;
                });
    }

    public static Mono<List<String>> getStringsMono(HttpMethod method, URI uri, MockClientHttpRequest request) {
        ArrayList<String> key = new ArrayList<>();
        key.add(method.name());
        key.add("HTTP/1.1");
        key.addAll(filterHeaders(request.getHeaders()));
        key.add(uri.toString());

        return Mono.just(key.toArray(new String[0]))
                .flatMap(keys -> {
                    if (!matchBodyRule(uri.toString())) {
                        return Mono.just(List.of(keys));
                    }
                    return request.getBodyAsString()
                            .switchIfEmpty(Mono.just(""))
                            .map((String body) -> {
                                String maskedBody = SettingsUtil.maskBody(body);
                                String safeBody = StringUtil.isText(maskedBody) ?
                                        escapeCharacterString(maskedBody) :
                                        toCharacterString(maskedBody.getBytes(StandardCharsets.UTF_8));

                                return Stream.concat(Stream.of(keys), Stream.of(safeBody))
                                        .collect(Collectors.toList());
                            });

                });
    }
}
