package org.anystub;

import org.anystub.mgmt.BaseManagerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.anystub.AnyStubFileLocator.discoverFile;
import static org.anystub.SettingsUtil.matchBodyRule;
import static org.anystub.StringUtil.addTextPrefix;
import static org.anystub.StringUtil.escapeCharacterString;
import static org.anystub.StringUtil.toCharacterString;


public class StubClientHttpConnector implements ClientHttpConnector {

    private static final String HEADER_MASK = "^[A-Za-z0-9\\-]+: .+";

    final ClientHttpConnector real;
    private Base fallbackBase = null;

    public StubClientHttpConnector(ClientHttpConnector real) {
        this.real = real;
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
                .map(h->headerToString(headers, h))
                .sorted()
                .collect(Collectors.toList());
    }

    public static String headerToString(HttpHeaders headers, String h) {
        return String.format("%s: %s", h, String.join(", ", headers.getOrEmpty(h)));
    }


    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {


        MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
        requestCallback.apply(request).block();

        List<String> key = getStrings(method, uri, request);

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

                            if (postHeader != null) {
                                byte[] bytes = StringUtil.recoverBinaryData(postHeader);
                                Charset charset = null;
                                MediaType contentType = clientResponse.getHeaders().getContentType();
                                if (contentType != null) {
                                    charset = contentType.getCharset();
                                }
                                if (charset == null) {
                                    charset = StandardCharsets.UTF_8;
                                }
                                clientResponse.setBody(new String(bytes, charset));
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
                                    .sorted(String::compareTo)
                                    .map(h -> headerToString(response.getHeaders(), h))
                                    .collect(Collectors.toList());

                            res.addAll(headers);

                            Flux<DataBuffer> body = response.getBody();

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
                            res.add(bodyString);

                            return res;
                        },
                key.toArray(new String[0])
        );

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


    private Base getBase() {
        AnyStubId s = discoverFile();
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




}
