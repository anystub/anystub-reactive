package org.anystub;

import org.anystub.mgmt.BaseManagerFactory;
import org.springframework.core.io.buffer.DataBuffer;
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
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.anystub.AnyStubFileLocator.discoverFile;
import static org.anystub.Util.HEADER_MASK;


public class StubClientHttpConnector implements ClientHttpConnector {



    final ClientHttpConnector real;
    private Base fallbackBase = null;

    public StubClientHttpConnector(ClientHttpConnector real) {
        this.real = real;
    }


    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {


        MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
        requestCallback.apply(request).block();

        List<String> key = Util.getStrings(method, uri, request);

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
                                    .map(h -> Util.headerToString(response.getHeaders(), h))
                                    .collect(Collectors.toList());

                            res.addAll(headers);

                            Flux<DataBuffer> body = response.getBody();

                            String bodyString = Util.extractString(body);
                            res.add(bodyString);

                            return res;
                        },
                key.toArray(new String[0])
        );

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
