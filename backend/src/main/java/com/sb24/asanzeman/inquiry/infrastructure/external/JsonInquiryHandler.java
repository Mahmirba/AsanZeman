package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import com.sb24.asanzeman.integration.inquiry.InquiryCallException;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every inquiry client has in common (5.1, 5.3): ask, read the answer as JSON, decide. An answer that cannot be
 * read as the service documents it is {@code FAILED} ({@code BAD_RESPONSE}), never a rejection; a service that did not
 * answer is {@code FAILED} with the kind of failure. The whole raw answer is logged (D22).
 */
abstract class JsonInquiryHandler implements InquiryHandler {

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(JsonInquiryHandler.class);

    private final InquiryType type;
    protected final InquiryClient client;

    JsonInquiryHandler(InquiryType type, InquiryClient client) {
        this.type = type;
        this.client = client;
    }

    @Override
    public InquiryType type() {
        return type;
    }

    @Override
    public boolean appliesTo(InquiryContext context) {
        return true;
    }

    /** Sends the request for this customer. */
    abstract InquiryReply ask(InquiryContext context);

    /** Decides from the answer. May throw anything when the answer is not what the service documents. */
    abstract InquiryOutcome decide(InquiryContext context, InquiryReply reply);

    @Override
    public InquiryOutcome execute(InquiryContext context) {
        InquiryReply reply;
        try {
            reply = ask(context);
        } catch (InquiryCallException e) {
            log.warn("inquiry {} got no answer: {}", type, e.kind());
            return InquiryOutcome.failed(type, e.kind() == InquiryCallException.Kind.TIMEOUT ? FailureKind.TIMEOUT
                    : FailureKind.UNAVAILABLE);
        } catch (RuntimeException e) {
            // the question itself could not be built: missing request data or a missing fixed field
            log.error("inquiry {} could not build its request", type, e);
            return InquiryOutcome.failed(type, FailureKind.UNAVAILABLE);
        }
        log.info("inquiry {} answered HTTP {}: {}", type, reply.status(), reply.body());
        try {
            return decide(context, reply);
        } catch (RuntimeException e) {
            log.warn("inquiry {} answer could not be read", type, e);
            return InquiryOutcome.failed(type, FailureKind.BAD_RESPONSE);
        }
    }

    /** The body as a JSON object; anything else (an HTML error page, an empty body) is not an answer. */
    static JsonNode json(InquiryReply reply) {
        JsonNode node = JSON.readTree(reply.body());
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("the answer is not a JSON object");
        }
        return node;
    }

    /** A field that is there and not {@code null}. */
    static boolean present(JsonNode node) {
        return !node.isMissingNode() && !node.isNull();
    }

    /** An HTTP status other than 200 is no answer to read (5.3 shared rules). */
    static void requireOk(InquiryReply reply) {
        if (reply.status() != 200) {
            throw new IllegalStateException("the service answered HTTP " + reply.status());
        }
    }
}
