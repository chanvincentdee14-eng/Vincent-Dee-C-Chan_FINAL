package chc;

import java.time.LocalDate;
import java.time.Period;

/**
 * A patient's personal details (OOP: encapsulation).
 * The fields are private and the class checks its own data, so nobody can build an invalid patient.
 * A {@link Visit} HAS-A Patient (composition).
 */
public class Patient {
    private final String fullName;
    private final String contact;
    private final LocalDate birthday;
    private final String address;

    public Patient(String fullName, String contact, LocalDate birthday, String address) {
        if (fullName == null || fullName.trim().isEmpty())
            throw new IllegalArgumentException("A patient needs a full name.");
        this.fullName = fullName.trim();
        this.contact = blankToNull(contact);
        this.birthday = birthday;
        this.address = blankToNull(address);
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    public String getFullName() { return fullName; }

    /** The contact number, or null when none was given. */
    public String getContact() { return contact; }

    public LocalDate getBirthday() { return birthday; }

    /** The address, or null when none was given. */
    public String getAddress() { return address; }

    /** Age in years, or -1 when the birthday is unknown. */
    public int getAge() {
        return birthday == null ? -1 : Period.between(birthday, LocalDate.now()).getYears();
    }

    /** True when the search text appears in the name or contact number (ignores upper/lower case). */
    public boolean matches(String text) {
        String q = text == null ? "" : text.trim().toLowerCase();
        return q.isEmpty() || fullName.toLowerCase().contains(q)
                || (contact != null && contact.toLowerCase().contains(q));
    }

    @Override public String toString() { return fullName; }
}
