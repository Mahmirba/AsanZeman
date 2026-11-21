package com.sb24.asanzeman.integration.http;

import java.net.Socket;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} of one bank service with its own timeouts and its own TLS setting (D38). Certificate
 * checking is switched off per service, never for the whole JVM.
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout, boolean skipTlsVerify) {
        return RestClient.builder().requestFactory(requestFactory(connectTimeout, readTimeout, skipTlsVerify))
                .build();
    }

    /** The request factory alone, for clients that need to add their own converters or error handling. */
    public static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout,
            boolean skipTlsVerify) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                httpClient(connectTimeout, skipTlsVerify));
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /** The JDK {@link HttpClient} alone, for clients that are not built on {@code RestClient} (the SOAP client). */
    public static HttpClient httpClient(Duration connectTimeout, boolean skipTlsVerify) {
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (skipTlsVerify) {
            http.sslContext(trustAllContext());
        }
        return http.build();
    }

    static SSLContext trustAllContext() {
        return trustAllContext("TLS");
    }

    static SSLContext trustAllContext(String protocol) {
        try {
            SSLContext context = SSLContext.getInstance(protocol);
            context.init(null, new TrustManager[] {new TrustAll()}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot create the TLS context", e);
        }
    }

    /** Accepts every certificate and host name. Used only when a service has {@code tls.skip-verify=true}. */
    static final class TrustAll extends X509ExtendedTrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // accepted on purpose
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // accepted on purpose
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // accepted on purpose
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // accepted on purpose
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // accepted on purpose
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // accepted on purpose
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
