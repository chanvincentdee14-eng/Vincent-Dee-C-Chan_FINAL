package chc;

import java.util.List;

/**
 * What the program needs from the place where accounts are kept (OOP: abstraction).
 * The screens depend on this interface, not on MySQL, so the storage could be changed
 * (for example to a file or a test double) without touching the screens.
 */
public interface UserRepository {
    /** Returns the user when the username and password are right, otherwise null. */
    User authenticate(String username, String password) throws ServiceException;

    List<User> list() throws ServiceException;

    void add(String username, String password, String role) throws ServiceException;

    void delete(int id, int currentUserId) throws ServiceException;

    void setRole(int id, String role, int currentUserId) throws ServiceException;

    void resetPassword(int id, String newPassword) throws ServiceException;
}
