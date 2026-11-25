package com.sb24.asanzeman.integration.inquiry;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * One inquiry service's HTTP calls (5.3). The caller gets the status and the body whatever the status is: what a 4xx
 * or an unexpected body means is the inquiry's decision. A dropped connection, a timeout or a 5xx is repeated up to
 * {@code retries} times (D4); when the calls run out, an {@link InquiryCallException} says which kind of failure it was.
 * The gateway bearer token is added by the {@link RestClient}'s interceptor.
 */
public class InquiryClient {

    private final RestClient rest;
    private final InquiryEndpoint endpoint;
    private final String url;
    private final Duration backoff;

    public InquiryClient(RestClient rest, String baseUrl, InquiryEndpoint endpoint, Duration backoff) {
        this.rest = rest;
        this.endpoint = endpoint;
        this.url = baseUrl + endpoint.path();
        this.backoff = backoff;
    }

    public InquiryEndpoint endpoint() {
        return endpoint;
    }

    /** {@code POST} of a JSON body, with extra headers (a few services want a channel code header). */
    public InquiryReply post(Object body, Map<String, String> headers) {
        return attempt(() -> rest.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> headers.forEach(h::set)).body(body).exchange((request, response) -> reply(response)));
    }

    /** {@code GET} of the configured path followed by {@code suffix} (the national code, for one service). */
    public InquiryReply get(String suffix) {
        return attempt(() -> rest.get().uri(url + suffix).accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> reply(response)));
    }

    private static InquiryReply reply(ClientHttpResponse response) throws IOException {
        return new InquiryReply(response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private InquiryReply attempt(Callable<InquiryReply> call) {
        RestClientException last = null;
        for (int attempt = 0; attempt <= endpoint.retries(); attempt++) {
            try {
                InquiryReply reply = call.call();
                if (reply.status() < 500) {
                    return reply;
                }
                last = new RestClientException("the service answered HTTP " + reply.status());
            } catch (RestClientException e) {
                last = e;
            } catch (Exception e) {
                throw new InquiryCallException(InquiryCallException.Kind.UNAVAILABLE, e);
            }
            if (attempt < endpoint.retries() && !pause()) {
                break;
            }
        }
        throw new InquiryCallException(timeout(last) ? InquiryCallException.Kind.TIMEOUT
                : InquiryCallException.Kind.UNAVAILABLE, last);
    }

    /** @return {@code false} when the run was cancelled meanwhile: no more calls */
    private boolean pause() {
        try {
            Thread.sleep(backoff);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean timeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
