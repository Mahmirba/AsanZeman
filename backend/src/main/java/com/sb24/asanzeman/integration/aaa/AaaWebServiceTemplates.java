package com.sb24.asanzeman.integration.aaa;

import java.net.http.HttpClient;
import java.time.Duration;
import org.apache.wss4j.dom.WSConstants;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.transport.http.JdkHttpClientMessageSender;

/**
 * Builds the {@link WebServiceTemplate} for AAAService (D39): SOAP 1.1 through SAAJ, JAXB for the classes generated
 * from the WSDL, and a WS-Security {@code UsernameToken} ({@code PasswordText}) for the AAA technical account. As in the
 * existing working client there is no timestamp, no nonce and no created time; a timestamp can be switched on if AAA
 * starts to require one.
 */
public final class AaaWebServiceTemplates {

    /** Packages of the JAXB classes generated from {@code src/main/resources/wsdl/aaa}. */
    static final String[] CONTEXT_PATHS = {
            "com.sb24.services.security.authorizationsrv",
            "com.sb24.services.security.authorizationsrv.storageinfoparam",
            "com.sb24.services.security.authorizationsrv.userparam",
            "com.sb24.services.security.authorizationsrv.roledataresult",
            "com.sb24.services.common.customentities.resulterrors",
            "com.microsoft.schemas._2003._10.serialization.arrays"};
    static final int TIMESTAMP_TTL_SECONDS = 300;

    private AaaWebServiceTemplates() {
    }

    public static WebServiceTemplate create(String url, String technicalUser, String technicalPassword,
            boolean timestamp, HttpClient httpClient, Duration readTimeout) {
        try {
            SaajSoapMessageFactory messageFactory = new SaajSoapMessageFactory();
            messageFactory.setSoapVersion(SoapVersion.SOAP_11);
            messageFactory.afterPropertiesSet();

            Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
            marshaller.setContextPaths(CONTEXT_PATHS);
            marshaller.afterPropertiesSet();

            Wss4jSecurityInterceptor security = new Wss4jSecurityInterceptor();
            security.setSecurementActions(timestamp ? "UsernameToken Timestamp" : "UsernameToken");
            security.setSecurementUsername(technicalUser);
            security.setSecurementPassword(technicalPassword);
            security.setSecurementPasswordType(WSConstants.PW_TEXT);
            security.setSecurementUsernameTokenNonce(false);
            security.setSecurementUsernameTokenCreated(false);
            security.setSecurementMustUnderstand(false);
            security.setSecurementTimeToLive(TIMESTAMP_TTL_SECONDS);
            security.afterPropertiesSet();

            JdkHttpClientMessageSender sender = new JdkHttpClientMessageSender(httpClient);
            sender.setRequestTimeout(readTimeout);

            WebServiceTemplate template = new WebServiceTemplate(messageFactory);
            template.setDefaultUri(url);
            template.setMarshaller(marshaller);
            template.setUnmarshaller(marshaller);
            template.setMessageSender(sender);
            template.setInterceptors(new ClientInterceptor[] {security});
            template.afterPropertiesSet();
            return template;
        } catch (Exception e) {
            throw new IllegalStateException("cannot build the AAA web service client", e);
        }
    }
}
