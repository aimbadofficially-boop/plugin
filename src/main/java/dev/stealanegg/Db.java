package dev.stealanegg;

import dev.stealanegg.PlayerData.EggRow;
import dev.stealanegg.PlayerData.PetRow;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/** SQLite persistence. Every state transition of an egg is an atomic conditional UPDATE (anti-duplication). */
public final class Db {
    private final Connection c;
    private final Logger log;

    public Db(File f, Logger log) throws SQLException {
        this.log = log;
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        c = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY, name TEXT, money REAL, pending REAL, speed REAL,"
                    + " treadmill INTEGER, trail TEXT, trails TEXT, plot INTEGER, pen_level INTEGER, slot_level INTEGER,"
                    + " hatched INTEGER, locked INTEGER, last_seen INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS eggs(id TEXT PRIMARY KEY, type TEXT, size REAL, owner TEXT, state TEXT, slot INTEGER, ready_at INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS pets(id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT, type TEXT, mutation TEXT, size REAL)");
            s.execute("CREATE INDEX IF NOT EXISTS eggs_owner ON eggs(owner)");
            s.execute("CREATE INDEX IF NOT EXISTS pets_owner ON pets(owner)");
        }
    }

    private void err(Exception e) { log.severe("DB error: " + e); }

    public synchronized void close() { try { c.close(); } catch (SQLException ignored) {} }

    public synchronized String meta(String k) {
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM meta WHERE k=?")) {
            ps.setString(1, k);
            try (ResultSet r = ps.executeQuery()) { return r.next() ? r.getString(1) : null; }
        } catch (SQLException e) { err(e); return null; }
    }

    public synchronized void setMeta(String k, String v) {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)")) {
            ps.setString(1, k); ps.setString(2, v); ps.executeUpdate();
        } catch (SQLException e) { err(e); }
    }

    public synchronized PlayerData load(UUID id) {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) return null;
                PlayerData d = new PlayerData(id, r.getString("name"));
                d.money = r.getDouble("money"); d.pending = r.getDouble("pending"); d.speed = r.getDouble("speed");
                d.treadmill = r.getInt("treadmill"); d.trail = r.getString("trail") == null ? "" : r.getString("trail");
                String tl = r.getString("trails");
                if (tl != null && !tl.isEmpty()) d.trails.addAll(Arrays.asList(tl.split(",")));
                d.plot = r.getInt("plot"); d.penLevel = r.getInt("pen_level"); d.slotLevel = r.getInt("slot_level");
                d.hatched = r.getInt("hatched"); d.locked = r.getInt("locked") == 1; d.lastSeen = r.getLong("last_seen");
                d.pets.addAll(pets(id)); d.eggs.addAll(eggs(id));
                return d;
            }
        } catch (SQLException e) { err(e); return null; }
    }

    public synchronized void save(PlayerData d) {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO players(uuid,name,money,pending,speed,treadmill,trail,trails,plot,pen_level,slot_level,hatched,locked,last_seen) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.uuid.toString()); ps.setString(2, d.name); ps.setDouble(3, d.money); ps.setDouble(4, d.pending);
            ps.setDouble(5, d.speed); ps.setInt(6, d.treadmill); ps.setString(7, d.trail); ps.setString(8, String.join(",", d.trails));
            ps.setInt(9, d.plot); ps.setInt(10, d.penLevel); ps.setInt(11, d.slotLevel); ps.setInt(12, d.hatched);
            ps.setInt(13, d.locked ? 1 : 0); ps.setLong(14, d.lastSeen);
            ps.executeUpdate();
        } catch (SQLException e) { err(e); }
    }

    public synchronized List<PetRow> pets(UUID id) {
        List<PetRow> l = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,type,mutation,size FROM pets WHERE owner=? ORDER BY id")) {
            ps.setString(1, id.toString());
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    PetRow p = new PetRow();
                    p.id = r.getLong(1); p.type = r.getString(2); p.mutation = r.getString(3); p.size = r.getDouble(4);
                    l.add(p);
                }
            }
        } catch (SQLException e) { err(e); }
        return l;
    }

    public synchronized List<EggRow> eggs(UUID id) {
        List<EggRow> l = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,type,size,state,slot,ready_at FROM eggs WHERE owner=?")) {
            ps.setString(1, id.toString());
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    EggRow e = new EggRow();
                    e.id = r.getString(1); e.type = r.getString(2); e.size = r.getDouble(3); e.state = r.getString(4);
                    e.slot = r.getInt(5); e.readyAt = r.getLong(6);
                    l.add(e);
                }
            }
        } catch (SQLException e) { err(e); }
        return l;
    }

    public synchronized boolean insertEgg(String id, String type, double size, UUID owner, String state, int slot, long readyAt) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO eggs(id,type,size,owner,state,slot,ready_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, id); ps.setString(2, type); ps.setDouble(3, size); ps.setString(4, owner.toString());
            ps.setString(5, state); ps.setInt(6, slot); ps.setLong(7, readyAt);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { err(e); return false; }
    }

    /** Atomic state change; only succeeds if the egg is still owned by `owner` in `from` state. */
    public synchronized boolean eggTransition(String id, String from, String to, UUID owner, int slot, long readyAt) {
        try (PreparedStatement ps = c.prepareStatement("UPDATE eggs SET state=?, slot=?, ready_at=? WHERE id=? AND owner=? AND state=?")) {
            ps.setString(1, to); ps.setInt(2, slot); ps.setLong(3, readyAt); ps.setString(4, id);
            ps.setString(5, owner.toString()); ps.setString(6, from);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { err(e); return false; }
    }

    /** Atomic ownership transfer (stealing). */
    public synchronized boolean eggSteal(String id, UUID fromOwner, String fromState, UUID toOwner) {
        try (PreparedStatement ps = c.prepareStatement("UPDATE eggs SET owner=?, state='CARRIED', slot=-1, ready_at=0 WHERE id=? AND owner=? AND state=?")) {
            ps.setString(1, toOwner.toString()); ps.setString(2, id); ps.setString(3, fromOwner.toString()); ps.setString(4, fromState);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { err(e); return false; }
    }

    public synchronized boolean deleteEgg(String id) {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM eggs WHERE id=?")) {
            ps.setString(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { err(e); return false; }
    }

    public synchronized void resetHatching() {
        try (Statement s = c.createStatement()) {
            s.execute("UPDATE eggs SET state='INCUBATING', ready_at=0 WHERE state='HATCHING'");
        } catch (SQLException e) { err(e); }
    }

    public synchronized void revertHatching(UUID owner) {
        try (PreparedStatement ps = c.prepareStatement("UPDATE eggs SET state='INCUBATING', ready_at=? WHERE owner=? AND state='HATCHING'")) {
            ps.setLong(1, System.currentTimeMillis()); ps.setString(2, owner.toString());
            ps.executeUpdate();
        } catch (SQLException e) { err(e); }
    }

    /** Atomically consumes a HATCHING egg and creates the pet. Returns pet id or -1. */
    public synchronized long hatch(String eggId, UUID owner, String type, String mut, double size) {
        try {
            c.setAutoCommit(false);
            long id = -1;
            try (PreparedStatement d = c.prepareStatement("DELETE FROM eggs WHERE id=? AND owner=? AND state='HATCHING'")) {
                d.setString(1, eggId); d.setString(2, owner.toString());
                if (d.executeUpdate() == 1) id = insertPetRaw(owner, type, mut, size);
            }
            if (id > 0) c.commit(); else c.rollback();
            return id;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            err(e);
            return -1;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private long insertPetRaw(UUID owner, String type, String mut, double size) throws SQLException {
        try (PreparedStatement i = c.prepareStatement("INSERT INTO pets(owner,type,mutation,size) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            i.setString(1, owner.toString()); i.setString(2, type); i.setString(3, mut); i.setDouble(4, size);
            i.executeUpdate();
            try (ResultSet k = i.getGeneratedKeys()) { return k.next() ? k.getLong(1) : -1; }
        }
    }

    public synchronized long insertPet(UUID owner, String type, String mut, double size) {
        try { return insertPetRaw(owner, type, mut, size); } catch (SQLException e) { err(e); return -1; }
    }

    public synchronized boolean deletePet(long id, UUID owner) {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM pets WHERE id=? AND owner=?")) {
            ps.setLong(1, id); ps.setString(2, owner.toString());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { err(e); return false; }
    }

    public synchronized void wipe(UUID id) {
        try (Statement s = c.createStatement()) {
            String u = "'" + id + "'";
            s.execute("DELETE FROM players WHERE uuid=" + u);
            s.execute("DELETE FROM eggs WHERE owner=" + u);
            s.execute("DELETE FROM pets WHERE owner=" + u);
        } catch (SQLException e) { err(e); }
    }

    public synchronized Set<Integer> usedPlots(UUID except) {
        Set<Integer> s = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT plot FROM players WHERE plot>=0 AND uuid<>?")) {
            ps.setString(1, except.toString());
            try (ResultSet r = ps.executeQuery()) { while (r.next()) s.add(r.getInt(1)); }
        } catch (SQLException e) { err(e); }
        return s;
    }

    /** Frees the plot of the longest-inactive player (older than cutoff). Returns plot or -1. */
    public synchronized int freeStalePlot(long cutoff, UUID except) {
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid,plot FROM players WHERE plot>=0 AND last_seen<? AND uuid<>? ORDER BY last_seen LIMIT 1")) {
            ps.setLong(1, cutoff); ps.setString(2, except.toString());
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) return -1;
                String u = r.getString(1); int plot = r.getInt(2);
                try (PreparedStatement up = c.prepareStatement("UPDATE players SET plot=-1 WHERE uuid=?")) {
                    up.setString(1, u); up.executeUpdate();
                }
                return plot;
            }
        } catch (SQLException e) { err(e); return -1; }
    }

    public synchronized UUID findByName(String name) {
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM players WHERE lower(name)=lower(?)")) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) { return r.next() ? UUID.fromString(r.getString(1)) : null; }
        } catch (SQLException e) { err(e); return null; }
    }
}
