package org.anystub;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
     *
     * @param headers
     * @return
     */
    public static List<String> filterHeaders(HttpHeaders headers){

        boolean currentAllHeaders = HttpGlobalSettings.globalAllHeaders;
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
            if ((settings == null || !settings.overrideGlobal()) && HttpGlobalSettings.globalHeaders != null) {
                headersToAdd.addAll(asList(HttpGlobalSettings.globalHeaders));
            }
        }


        return headersToAdd.stream()
                .map(h-> headerToString(headers, h))
                .sorted()
                .collect(Collectors.toList());
    }

    public static String headerToString(HttpHeaders headers, String h) {
        return String.format("%s: %s", h, String.join(", ", headers.getOrEmpty(h)));
    }


    public static String extractString(Flux<DataBuffer> body) {
        List<byte[]> block = body.map(dataBuffer ->
                        StringUtil.readStream(dataBuffer.asInputStream(true)))
                .collectList()
                .block();

        byte[] bodyContext;
        if (block == null || block.isEmpty()) {
            bodyContext = new byte[0];
        } else {
            bodyContext =
                    block.stream()
                            .reduce(new byte[0], (result, bytes2) -> {
                                int v = result.length;
                                result = Arrays.copyOf(result, v + bytes2.length);
                                System.arraycopy(bytes2, 0, result, v, bytes2.length);
                                return result;
                            });
        }


        String bodyString = toCharacterString(bodyContext);
        if(bodyString.matches(HEADER_MASK)) {
            bodyString = addTextPrefix(bodyString);
        }
        return bodyString;
    }

    public static List<String> getStrings(HttpMethod method, URI uri, MockClientHttpRequest request) {
        ArrayList<String> key = new ArrayList<>();
        key.add(method.name());
        key.add("HTTP/1.1");
        key.addAll(filterHeaders(request.getHeaders()));
        key.add(uri.toString());


        if (matchBodyRule(uri.toString())) {
            String body = request.getBodyAsString()
                    .blockOptional().orElse("");
            body = SettingsUtil.maskBody(body);
            if (StringUtil.isText(body)) {
                key.add(escapeCharacterString(body));
            } else {
                key.add(toCharacterString(body.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return key;
    }
}
