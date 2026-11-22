package chc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One visit of a patient to the center (one row of the visits table).
 * Encapsulated: all fields are private and read through getters.
 */
public class Visit {
    private final int id;
    private final String queueNo;
    private final long lineSeq;
    private final Patient patient;
    private final Status status;
    private final String previousNumbers;
    private final String registeredBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime calledAt;
    private final LocalDateTime finishedAt;

    public Visit(int id, String queueNo, long lineSeq, Patient patient, Status status, String previousNumbers,
                 String registeredBy, LocalDateTime createdAt, LocalDateTime calledAt, LocalDateTime finishedAt) {
        this.id = id;
        this.queueNo = queueNo;
        this.lineSeq = lineSeq;
        this.patient = patient;
        this.status = status;
        this.previousNumbers = previousNumbers;
        this.registeredBy = registeredBy;
        this.createdAt = createdAt;
        this.calledAt = calledAt;
        this.finishedAt = finishedAt;
    }

    /** A new, still-waiting visit that has just been registered. */
    public Visit(int id, String queueNo, Patient patient, Status status, LocalDateTime createdAt) {
        this(id, queueNo, 0, patient, status, null, null, createdAt, null, null);
    }

    /** Builds a Visit from the current row of a database result. */
    static Visit from(ResultSet rs) throws SQLException {
        java.sql.Date b = rs.getDate("birthday");
        Patient p = new Patient(rs.getString("full_name"), rs.getString("contact"),
                b == null ? null : b.toLocalDate(), rs.getString("address"));
        return new Visit(rs.getInt("id"), rs.getString("queue_no"), rs.getLong("line_seq"), p,
                Status.valueOf(rs.getString("status")), rs.getString("previous_numbers"),
                rs.getString("registered_by"), ts(rs.getTimestamp("created_at")),
                ts(rs.getTimestamp("called_at")), ts(rs.getTimestamp("finished_at")));
    }

    private static LocalDateTime ts(Timestamp t) { return t == null ? null : t.toLocalDateTime(); }

    public int getId() { return id; }
    public String getQueueNo() { return queueNo; }
    public long getLineSeq() { return lineSeq; }
    public Patient getPatient() { return patient; }
    public Status getStatus() { return status; }
    public String getPreviousNumbers() { return previousNumbers; }
    public String getRegisteredBy() { return registeredBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCalledAt() { return calledAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }

    // shortcuts to the patient's details
    public String getFullName() { return patient.getFullName(); }
    public String getContact() { return patient.getContact(); }
    public LocalDate getBirthday() { return patient.getBirthday(); }
    public String getAddress() { return patient.getAddress(); }

    /** Minutes between registration and being called, or -1 when not yet called. */
    public long waitMinutes() {
        if (createdAt == null || calledAt == null) return -1;
        return Math.max(0, Duration.between(createdAt, calledAt).toMinutes());
    }

    /** Minutes this patient has been waiting so far (for active visits). */
    public long waitingSoFarMinutes() {
        if (createdAt == null) return 0;
        LocalDateTime end = calledAt != null ? calledAt : LocalDateTime.now();
        return Math.max(0, Duration.between(createdAt, end).toMinutes());
    }
}
