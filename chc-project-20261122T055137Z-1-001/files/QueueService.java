package chc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The heart of the program: a FIFO queue of patients stored in the database.
 *
 *   enqueue  -> offer() a new patient at the REAR of the line
 *   callNext -> poll()  the patient at the FRONT of the line
 *   display  -> the waiting list, front to rear
 *
 * The in-memory LinkedList (used as a Queue) mirrors the visits table, so every
 * change is saved to MySQL immediately and reloaded from it when other computers change data.
 */
public class QueueService {
    private static final String ACTIVE = "('WAITING','SERVING','ON_HOLD')";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Counts shown on the dashboard. */
    public static class Stats {
        private int waiting, serving, onHold, completedToday, removedToday, visitsToday, allVisits, allCompleted;

        public int getWaiting() { return waiting; }
        public int getServing() { return serving; }
        public int getOnHold() { return onHold; }
        public int getCompletedToday() { return completedToday; }
        public int getRemovedToday() { return removedToday; }
        public int getVisitsToday() { return visitsToday; }
        public int getAllVisits() { return allVisits; }
        public int getAllCompleted() { return allCompleted; }
    }

    private final LinkedList<Visit> waiting = new LinkedList<Visit>();   // the FIFO queue
    private final List<Visit> serving = new ArrayList<Visit>();
    private final List<Visit> onHold = new ArrayList<Visit>();
    private Stats stats = new Stats();
    private String signature = "";
    private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

    public void addListener(Runnable r) { listeners.add(r); }

    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() {
        for (Runnable r : listeners) r.run();
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reloads the queue from the database and makes sure the front of the line is being served:
     * when nobody is Serving, the first Waiting patient becomes Serving automatically, the next one
     * is "Next to Serve" and everybody else is Waiting. Returns true when something changed.
     */
    public synchronized boolean reload() throws ServiceException {
        boolean changed = load();
        if (autoServe() != null) changed = true;
        return changed;
    }

    /** Serves the patient at the front of the line when nobody is being served. Returns that patient or null. */
    private Visit autoServe() throws ServiceException {
        if (!serving.isEmpty()) return null;
        Visit v = waiting.peek();
        if (v == null) return null;
        int updated;
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(
                "UPDATE visits SET status='SERVING', called_at=NOW() WHERE id=? AND status='WAITING' "
                        + "AND (SELECT COUNT(*) FROM (SELECT id FROM visits WHERE status='SERVING') t) = 0")) {
            ps.setInt(1, v.getId());
            updated = ps.executeUpdate();
        } catch (SQLException e) {
            throw db(e);
        }
        load();   // another computer may have served someone first: just show what the database says
        return updated == 0 ? null : v;
    }

    /** Reads the queue from the database (no changes). Returns true when something changed. */
    private boolean load() throws ServiceException {
        List<Visit> active = new ArrayList<Visit>();
        Stats s = new Stats();
        try (Connection c = Db.get()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM visits WHERE status IN " + ACTIVE
                         + " ORDER BY line_seq, id")) {
                while (rs.next()) active.add(Visit.from(rs));
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT "
                                 + "SUM(status='WAITING'), SUM(status='SERVING'), SUM(status='ON_HOLD'), "
                                 + "SUM(status='COMPLETED' AND DATE(finished_at)=CURDATE()), "
                                 + "SUM(status IN ('DEQUEUED','DELETED') AND DATE(finished_at)=CURDATE()), "
                                 + "SUM(DATE(created_at)=CURDATE()), COUNT(*), SUM(status='COMPLETED') "
                                 + "FROM visits")) {
                if (rs.next()) {
                    s.waiting = rs.getInt(1); s.serving = rs.getInt(2); s.onHold = rs.getInt(3);
                    s.completedToday = rs.getInt(4); s.removedToday = rs.getInt(5);
                    s.visitsToday = rs.getInt(6); s.allVisits = rs.getInt(7); s.allCompleted = rs.getInt(8);
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("Cannot reach the database. Is MySQL running in XAMPP?", e);
        }
        waiting.clear(); serving.clear(); onHold.clear();
        StringBuilder sig = new StringBuilder();
        for (Visit v : active) {
            if (v.getStatus() == Status.WAITING) waiting.offer(v);          // enqueue in line order
            else if (v.getStatus() == Status.SERVING) serving.add(v);
            else onHold.add(v);
            sig.append(v.getId()).append(':').append(v.getStatus()).append(':').append(v.getQueueNo()).append(':')
               .append(v.getFullName()).append(':').append(v.getContact()).append(':').append(v.getAddress()).append(';');
        }
        sig.append(s.completedToday).append(',').append(s.removedToday).append(',').append(s.visitsToday)
           .append(',').append(s.allVisits).append(',').append(s.allCompleted);
        stats = s;
        boolean changed = !sig.toString().equals(signature);
        signature = sig.toString();
        return changed;
    }

    /** The active visit with this queue number (serving or waiting), or null. */
    private Visit findActive(String qno) {
        for (Visit v : serving) if (v.getQueueNo().equals(qno)) return v;
        for (Visit v : waiting) if (v.getQueueNo().equals(qno)) return v;
        return null;
    }

    private void reloadAndFire() {
        try { reload(); } catch (ServiceException ignored) { /* poller will report */ }
        fire();
    }

    // ------------------------------------------------------------------ snapshots

    public synchronized List<Visit> getWaiting() { return new ArrayList<Visit>(waiting); }

    public synchronized Visit getServing() { return serving.isEmpty() ? null : serving.get(0); }

    public synchronized List<Visit> getServingList() { return new ArrayList<Visit>(serving); }

    public synchronized List<Visit> getOnHold() { return new ArrayList<Visit>(onHold); }

    /** Front of the line without removing it (queue peek). */
    public synchronized Visit peekNext() { return waiting.peek(); }

    /** Serving first, then the waiting line (front to rear), then on hold. */
    public synchronized List<Visit> getActive() {
        List<Visit> all = new ArrayList<Visit>();
        all.addAll(serving);
        all.addAll(waiting);
        all.addAll(onHold);
        return all;
    }

    public synchronized Stats getStats() { return stats; }


    // ------------------------------------------------------------------ validation

    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }

    private void validate(String name, String contact, LocalDate birthday, String address) throws ServiceException {
        if (name.length() < 2) throw new ServiceException("Full name is required (at least 2 characters).");
        if (name.length() > 100) throw new ServiceException("Full name is too long (100 characters maximum).");
        if (!contact.isEmpty() && !contact.matches("[0-9+()\\-\\s]{7,20}"))
            throw new ServiceException("Contact number may only contain digits, spaces, + ( ) - and be 7-20 characters.");
        if (birthday != null && birthday.isAfter(LocalDate.now()))
            throw new ServiceException("Birthday cannot be in the future.");
        if (address.length() > 255) throw new ServiceException("Address is too long (255 characters maximum).");
    }

    /** Duplicate names among ACTIVE patients are blocked. */
    private void checkDuplicate(Connection c, String name, int exceptId) throws SQLException, ServiceException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT queue_no FROM visits WHERE LOWER(full_name) = LOWER(?) AND status IN " + ACTIVE
                        + " AND id <> ? LIMIT 1")) {
            ps.setString(1, name);
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new ServiceException(
                        "\"" + name + "\" is already in the queue as " + rs.getString(1) + ".");
            }
        }
    }

    private static ServiceException db(SQLException e) {
        return new ServiceException("Database error: " + e.getMessage(), e);
    }

    // ------------------------------------------------------------------ operations

    /** ENQUEUE: registers a patient, gives a queue number and puts them at the rear of the line. */
    public synchronized Visit enqueue(String name, String contact, LocalDate birthday, String address, String byUser)
            throws ServiceException {
        name = clean(name); contact = clean(contact); address = clean(address);
        validate(name, contact, birthday, address);
        String qno;
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                checkDuplicate(c, name, 0);
                long n = Db.nextValue(c, "qno_" + LocalDate.now().format(DAY));
                long seq = Db.nextValue(c, "line_seq");
                qno = String.format("Q%03d", n);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO visits(queue_no, line_seq, full_name, contact, birthday, address, status, registered_by) "
                                + "VALUES (?,?,?,?,?,?, 'WAITING', ?)")) {
                    ps.setString(1, qno);
                    ps.setLong(2, seq);
                    ps.setString(3, name);
                    ps.setString(4, contact.isEmpty() ? null : contact);
                    ps.setDate(5, birthday == null ? null : java.sql.Date.valueOf(birthday));
                    ps.setString(6, address.isEmpty() ? null : address);
                    ps.setString(7, byUser);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (ServiceException | SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw db(e);
        }
        reloadAndFire();
        return findActive(qno);
    }

    /** EDIT / UPDATE: changes patient details; the queue number and position stay the same. */
    public synchronized void update(int id, String name, String contact, LocalDate birthday, String address)
            throws ServiceException {
        name = clean(name); contact = clean(contact); address = clean(address);
        validate(name, contact, birthday, address);
        try (Connection c = Db.get()) {
            requireActive(c, id);
            checkDuplicate(c, name, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE visits SET full_name=?, contact=?, birthday=?, address=? WHERE id=?")) {
                ps.setString(1, name);
                ps.setString(2, contact.isEmpty() ? null : contact);
                ps.setDate(3, birthday == null ? null : java.sql.Date.valueOf(birthday));
                ps.setString(4, address.isEmpty() ? null : address);
                ps.setInt(5, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw db(e);
        }
        reloadAndFire();
    }

    /** CALL NEXT: serves the front of the line. Normally this already happened automatically. */
    public synchronized Visit callNext() throws ServiceException {
        load();
        if (!serving.isEmpty())
            throw new ServiceException(serving.get(0).getQueueNo() + " is still being served. Press Done first.");
        Visit v = autoServe();
        if (v == null) throw new ServiceException("No patient is waiting.");
        fire();
        return v;
    }

    /** DONE: completes the consultation; the next waiting patient is served automatically. */
    public synchronized Visit complete(int id) throws ServiceException {
        try (Connection c = Db.get()) {
            requireStatus(c, id, "SERVING", "Only the patient being served can be marked Done.");
            finish(c, id, "COMPLETED");
        } catch (SQLException e) {
            throw db(e);
        }
        reload();   // serves the next patient
        Visit next = serving.isEmpty() ? null : serving.get(0);
        fire();
        return next;
    }

    /** HOLD: the patient steps out of the line. If they were being served, the next patient is called. */
    public synchronized Visit hold(int id) throws ServiceException {
        boolean wasServing;
        try (Connection c = Db.get()) {
            String st = statusOf(c, id);
            if (!"WAITING".equals(st) && !"SERVING".equals(st))
                throw new ServiceException("Only a waiting or serving patient can be placed on hold.");
            wasServing = "SERVING".equals(st);
            try (PreparedStatement ps = c.prepareStatement("UPDATE visits SET status='ON_HOLD' WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw db(e);
        }
        reload();   // when the served patient was put on hold, the next patient is served automatically
        Visit next = (wasServing && !serving.isEmpty()) ? serving.get(0) : null;
        fire();
        return next;
    }

    /** RETURN: a held patient goes back to the REAR of the line with a NEW queue number. */
    public synchronized Visit returnToLine(int id) throws ServiceException {
        String newNo;
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                String oldNo = null;
                String prev = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT queue_no, previous_numbers, status FROM visits WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ServiceException("That visit no longer exists.");
                        if (!"ON_HOLD".equals(rs.getString(3)))
                            throw new ServiceException("Only a patient who is On Hold can return to the line.");
                        oldNo = rs.getString(1);
                        prev = rs.getString(2);
                    }
                }
                long n = Db.nextValue(c, "qno_" + LocalDate.now().format(DAY));
                long seq = Db.nextValue(c, "line_seq");
                newNo = String.format("Q%03d", n);
                String history = prev == null || prev.isEmpty() ? oldNo : prev + ", " + oldNo;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE visits SET status='WAITING', queue_no=?, line_seq=?, previous_numbers=?, called_at=NULL WHERE id=?")) {
                    ps.setString(1, newNo);
                    ps.setLong(2, seq);
                    ps.setString(3, history.length() > 120 ? history.substring(history.length() - 120) : history);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (ServiceException | SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw db(e);
        }
        reloadAndFire();
        return findActive(newNo);
    }

    /** DEQUEUE: the patient leaves before consultation. The visit stays in Records (red). */
    public synchronized void dequeue(int id) throws ServiceException { remove(id, "DEQUEUED"); }

    /** DELETE: a mistaken registration. The visit stays in Records (red). */
    public synchronized void delete(int id) throws ServiceException { remove(id, "DELETED"); }

    private void remove(int id, String newStatus) throws ServiceException {
        try (Connection c = Db.get()) {
            requireActive(c, id);
            finish(c, id, newStatus);
        } catch (SQLException e) {
            throw db(e);
        }
        reload();
        fire();
    }

    // ------------------------------------------------------------------ helpers

    private void finish(Connection c, int id, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE visits SET status=?, finished_at=NOW() WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private String statusOf(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM visits WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private void requireActive(Connection c, int id) throws SQLException, ServiceException {
        String st = statusOf(c, id);
        if (st == null || !(st.equals("WAITING") || st.equals("SERVING") || st.equals("ON_HOLD")))
            throw new ServiceException("That visit is no longer in the queue.");
    }

    private void requireStatus(Connection c, int id, String wanted, String message) throws SQLException, ServiceException {
        if (!wanted.equals(statusOf(c, id))) throw new ServiceException(message);
    }

    // ------------------------------------------------------------------ records

    /** RECORDS: every visit, newest first, with optional filters (any argument may be null / empty). */
    public List<Visit> records(LocalDate from, LocalDate to, Status status, String text) throws ServiceException {
        StringBuilder sql = new StringBuilder("SELECT * FROM visits WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (from != null) { sql.append(" AND DATE(created_at) >= ?"); args.add(java.sql.Date.valueOf(from)); }
        if (to != null) { sql.append(" AND DATE(created_at) <= ?"); args.add(java.sql.Date.valueOf(to)); }
        if (status != null) { sql.append(" AND status = ?"); args.add(status.name()); }
        text = clean(text);
        if (!text.isEmpty()) {
            sql.append(" AND (full_name LIKE ? OR queue_no LIKE ? OR contact LIKE ? OR address LIKE ?)");
            String like = "%" + text + "%";
            for (int i = 0; i < 4; i++) args.add(like);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        List<Visit> out = new ArrayList<Visit>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Visit.from(rs));
            }
        } catch (SQLException e) {
            throw db(e);
        }
        return out;
    }
}
