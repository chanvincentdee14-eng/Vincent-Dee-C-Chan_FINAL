package chc;

/** A problem that should be shown to the user in plain words. */
public class ServiceException extends Exception {
    public ServiceException(String message) { super(message); }
    public ServiceException(String message, Throwable cause) { super(message, cause); }
}
