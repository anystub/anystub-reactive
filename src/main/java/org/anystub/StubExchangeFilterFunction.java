package org.anystub;

import org.anystub.mgmt.BaseManagerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static org.anystub.AnyStubFileLocator.discoverFile;
import static org.anystub.Util.HEADER_MASK;
import static org.anystub.Util.headerToString;

public class StubExchangeFilterFunction implements ExchangeFilterFunction {
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        HttpMethod method = request.method();
        URI uri = request.url();

        MockClientHttpRequest r = new MockClientHttpRequest(method, uri);
        request.writeTo(r, ExchangeStrategies.withDefaults()).block();
        List<String> key = Util.getStrings(method, uri, r);

        return getBase()
                .request2(
                        () -> next.exchange(request),
                        (Iterable<String> iterable) -> {
                            Iterator<String> iterator = iterable.iterator();
                            String[] protocol = iterator.next().split("[/.]");
                            String code = iterator.next();
                            String reason = iterator.next();


                            ClientResponse.Builder builder = ClientResponse.create(HttpStatus.valueOf(Integer.parseInt(code)));

                            MediaType contentType = null;
                            String postHeader = null;
                            while (iterator.hasNext()) {
                                String header;
                                header = iterator.next();
                                if (!header.matches(HEADER_MASK)) {
                                    postHeader = header;
                                    break;
                                }

                                int i = header.indexOf(": ");
                                builder = builder.header(header.substring(0, i), header.substring(i + 2));
                                if (contentType == null
                                        && StringUtils.hasLength(header.substring(0, i))
                                        && header.substring(0, i).equals("Content-Type")) {
                                    contentType = MediaType.parseMediaType(header.substring(i + 2));
                                }
                            }

                            if (postHeader != null) {
                                byte[] bytes = StringUtil.recoverBinaryData(postHeader);
                                Charset charset = null;
                                if (contentType != null) {
                                    charset = contentType.getCharset();
                                }
                                if (charset == null) {
                                    charset = StandardCharsets.UTF_8;
                                }
                                builder.body(new String(bytes, charset));
                            }
                            return Mono.just(builder.build());
                        },
                        (Mono<ClientResponse> clientResponseMono) -> {
                            List<String> res = new ArrayList<>();
                            ClientResponse response = clientResponseMono.block();
                            if (response == null) {
                                return res;
                            }

                            res.add("HTTP/1.1");
                            res.add(Integer.toString(response.rawStatusCode()));
                            res.add(response.statusCode().getReasonPhrase());

                            List<String> headers = response.headers()
                                    .asHttpHeaders()
                                    .keySet()
                                    .stream()
                                    .sorted(String::compareTo)
                                    .map(h -> headerToString(response
                                            .headers()
                                            .asHttpHeaders(), h))
                                    .collect(Collectors.toList());

                            res.addAll(headers);

                            Flux<DataBuffer> body = response.bodyToFlux(DataBuffer.class);

                            String bodyString = Util.extractString(body);
                            res.add(bodyString);

                            return res;
                        },
                        key.toArray(new String[0]));
    }

    @Override
    public ExchangeFilterFunction andThen(ExchangeFilterFunction afterFilter) {
        return ExchangeFilterFunction.super.andThen(afterFilter);
    }

    @Override
    public ExchangeFunction apply(ExchangeFunction exchange) {
        return ExchangeFilterFunction.super.apply(exchange);
    }


    private Base getBase() {
        AnyStubId s = discoverFile();
        if (s != null) {
            return BaseManagerFactory
                    .getBaseManager()
                    .getBase(s.filename())
                    .constrain(s.requestMode());
        }

        return BaseManagerFactory
                .getBaseManager()
                .getBase();
    }
}
