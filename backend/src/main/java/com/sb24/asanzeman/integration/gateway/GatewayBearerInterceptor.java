package com.sb24.asanzeman.integration.gateway;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Adds {@code Authorization: Bearer <token>} to the inquiry clients (5.2). A 401 clears the cached token, fetches a
 * new one once and repeats the call once; a second 401 is handed back to the caller.
 */
public class GatewayBearerInterceptor implements ClientHttpRequestInterceptor {

    private final GatewayTokenProvider tokens;

    public GatewayBearerInterceptor(GatewayTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getStatusCode() != HttpStatus.UNAUTHORIZED) {
            return response;
        }
        response.close();
        tokens.invalidate();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
        return execution.execute(request, body);
    }
}
