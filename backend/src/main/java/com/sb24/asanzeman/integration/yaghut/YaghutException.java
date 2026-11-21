package com.sb24.asanzeman.integration.yaghut;

/**
 * A business fault that Yaghut reported as JSON {@code {"exception": "...", "success": false}} (5.4). Technical
 * problems (timeouts, 5xx, a proxy's HTML page, unknown faults) are not this: they are
 * {@code TemporarilyUnavailableException}.
 */
public class YaghutException extends RuntimeException {

    /** The faults the plan decides on; matched on the {@code exception} field only. */
    public enum Fault {
        INVALID_SESSION("InvalidSessionWSException"),
        INVALID_CREDENTIALS("InvalidCredentialsWSException"),
        SUSPECTED_CUSTOMER("SuspectedCustomerWSException"),
        INVALID_CUSTOMER("InvalidCustomerWSException"),
        SERVICE_ACCESS_DENIED("ServiceAccessIsDeniedWSException");

        private final String wireName;

        Fault(String wireName) {
            this.wireName = wireName;
        }

        /** @return the fault with this Yaghut name, or {@code null} when the name is unknown */
        static Fault from(String name) {
            for (Fault fault : values()) {
                if (fault.wireName.equals(name)) {
                    return fault;
                }
            }
            return null;
        }

        public String wireName() {
            return wireName;
        }
    }

    private final Fault fault;

    public YaghutException(Fault fault, String yaghutMessage) {
        super(fault.wireName() + ": " + yaghutMessage);
        this.fault = fault;
    }

    public Fault fault() {
        return fault;
    }
}
