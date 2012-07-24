package hudson.plugins.clearcase.util;


public class DeliverError extends ClearToolError {

    private static final long serialVersionUID = 1039432448619362059L;

    public DeliverError(String message, Throwable cause) {
        super(message, cause);
    }

    public DeliverError(String message) {
        super(message);
    }
}
