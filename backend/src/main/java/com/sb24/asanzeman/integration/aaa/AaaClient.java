package com.sb24.asanzeman.integration.aaa;

import com.microsoft.schemas._2003._10.serialization.arrays.ArrayOfstring;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.services.common.customentities.resulterrors.ArrayOfResultErrors;
import com.sb24.services.common.customentities.resulterrors.ResultErrors;
import com.sb24.services.security.authorizationsrv.GetRoleWindows;
import com.sb24.services.security.authorizationsrv.GetRoleWindowsResponse;
import com.sb24.services.security.authorizationsrv.ObjectFactory;
import com.sb24.services.security.authorizationsrv.roledataresult.RoleDataResult;
import com.sb24.services.security.authorizationsrv.storageinfoparam.StorageInfoParam;
import com.sb24.services.security.authorizationsrv.userparam.UserParam;
import jakarta.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.XmlMappingException;
import org.springframework.ws.WebServiceException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

/**
 * AAAService {@code GetRoleWindows} (6.2, D39): SOAP 1.1 document/literal. The request carries the application and
 * store names from configuration and the user name and password the branch user typed; the technical account's
 * WS-Security {@code UsernameToken} is added by the template's interceptor.
 */
public class AaaClient {

    static final String SERVICE = "AAA";
    static final String SOAP_ACTION = "http://www.sb24.com/Services/Security/AuthorizationSrv/GetRoleWindows/Request";
    private static final Logger log = LoggerFactory.getLogger(AaaClient.class);

    private final WebServiceTemplate template;
    private final String applicationName;
    private final String storeName;
    private final ObjectFactory factory = new ObjectFactory();
    private final com.sb24.services.security.authorizationsrv.storageinfoparam.ObjectFactory storageFactory =
            new com.sb24.services.security.authorizationsrv.storageinfoparam.ObjectFactory();
    private final com.sb24.services.security.authorizationsrv.userparam.ObjectFactory userFactory =
            new com.sb24.services.security.authorizationsrv.userparam.ObjectFactory();

    public AaaClient(WebServiceTemplate template, String applicationName, String storeName) {
        this.template = template;
        this.applicationName = applicationName;
        this.storeName = storeName;
    }

    /**
     * @return AAA's answer; refused credentials are an answer with errors, not an exception
     * @throws TemporarilyUnavailableException on a SOAP fault, timeout, connection error or an unreadable or empty
     *         answer
     */
    public AaaRoleResult getRoleWindows(String userName, String password) {
        GetRoleWindows request = factory.createGetRoleWindows();
        StorageInfoParam storage = storageFactory.createStorageInfoParam();
        storage.setApplicationName(storageFactory.createStorageInfoParamApplicationName(applicationName));
        storage.setStoreName(storageFactory.createStorageInfoParamStoreName(storeName));
        UserParam user = userFactory.createUserParam();
        user.setUserName(userFactory.createUserParamUserName(userName));
        user.setPassword(userFactory.createUserParamPassword(password));
        request.setStorageparam(factory.createGetRoleWindowsStorageparam(storage));
        request.setParam(factory.createGetRoleWindowsParam(user));

        Object answer;
        try {
            answer = template.marshalSendAndReceive(request, new SoapActionCallback(SOAP_ACTION));
        } catch (WebServiceException | XmlMappingException e) {
            throw technical(e);
        }
        RoleDataResult result = answer instanceof GetRoleWindowsResponse response
                ? value(response.getGetRoleWindowsResult()) : null;
        if (result == null) {
            log.warn("AAA GetRoleWindows answered without a result: {}", answer);
            throw technical(new IllegalStateException("AAA answered without GetRoleWindowsResult"));
        }
        return new AaaRoleResult(errors(value(result.getDetailError())), roles(value(result.getUserRoles())));
    }

    private static List<AaaRoleResult.AaaError> errors(ArrayOfResultErrors array) {
        List<AaaRoleResult.AaaError> errors = new ArrayList<>();
        if (array != null) {
            for (ResultErrors e : array.getResultErrors()) {
                errors.add(new AaaRoleResult.AaaError(e.getErrorCode() == null ? 0 : e.getErrorCode(),
                        value(e.getErrorDesc()), value(e.getInnerException())));
            }
        }
        return errors;
    }

    private static List<String> roles(ArrayOfstring array) {
        return array == null ? List.of() : array.getString().stream().filter(s -> s != null).toList();
    }

    /** A nillable element's value, or {@code null} when the element is absent or {@code xsi:nil}. */
    private static <T> T value(JAXBElement<T> element) {
        return element == null || element.isNil() ? null : element.getValue();
    }

    private static TemporarilyUnavailableException technical(Throwable cause) {
        return new TemporarilyUnavailableException(SERVICE, cause);
    }
}
