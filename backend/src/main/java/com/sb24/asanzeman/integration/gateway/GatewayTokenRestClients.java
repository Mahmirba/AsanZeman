package com.sb24.asanzeman.integration.gateway;

import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Spring Security's OAuth2 client sends the token request through a {@link RestClient}. A client with our own timeouts
 * and TLS setting (D38) has to carry the same converters and error handler as the library's default one: the form
 * converter for the request, the token-response converter, and the handler that turns an OAuth2 error body such as
 * {@code {"error":"invalid_client"}} into an {@code OAuth2AuthorizationException}.
 */
public final class GatewayTokenRestClients {

    private GatewayTokenRestClients() {
    }

    public static RestClient.Builder configure(RestClient.Builder builder) {
        return builder
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler());
    }
}
