package org.example.shorter;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LinkRepository {
    private final String jdbcUrl;

    public LinkRepository(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        init();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void init() {
        String sql =
            """
        CREATE TABLE IF NOT EXISTS links (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_uuid TEXT NOT NULL,
          code TEXT NOT NULL UNIQUE,
          original_url TEXT NOT NULL,
          created_at_ms INTEGER NOT NULL,
          expires_at_ms INTEGER NOT NULL,
          max_clicks INTEGER NOT NULL,
          clicks INTEGER NOT NULL,
          active INTEGER NOT NULL
        );
        """;
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("DB init error: " + e.getMessage(), e);
        }
    }

    public Link insert(Link l) {
        String sql =
            """
        INSERT INTO links(user_uuid, code, original_url, created_at_ms, expires_at_ms, max_clicks, clicks, active)
        VALUES(?,?,?,?,?,?,?,?)
        """;
        try (Connection c = conn();
             PreparedStatement ps =
                 c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, l.userUuid);
            ps.setString(2, l.code);
            ps.setString(3, l.originalUrl);
            ps.setLong(4, l.createdAtMs);
            ps.setLong(5, l.expiresAtMs);
            ps.setInt(6, l.maxClicks);
            ps.setInt(7, l.clicks);
            ps.setInt(8, l.active);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) l.id = rs.getLong(1);
            }
            return l;
        } catch (SQLException e) {
            throw new RuntimeException("DB insert error: " + e.getMessage(), e);
        }
    }

    public Optional<Link> findByCode(String code) {
        String sql = "SELECT * FROM links WHERE code = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB query error: " + e.getMessage(), e);
        }
    }

    public List<Link> listByUser(String userUuid) {
        String sql = "SELECT * FROM links WHERE user_uuid = ? ORDER BY id DESC";
        List<Link> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("DB list error: " + e.getMessage(), e);
        }
    }

    public boolean deleteByCodeAndUser(String code, String userUuid) {
        String sql = "DELETE FROM links WHERE code = ? AND user_uuid = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, userUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("DB delete error: " + e.getMessage(), e);
        }
    }

    public boolean updateLimit(String code, String userUuid, int newLimit) {
        String sql = "UPDATE links SET max_clicks = ? WHERE code = ? AND user_uuid = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newLimit);
            ps.setString(2, code);
            ps.setString(3, userUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("DB update limit error: " + e.getMessage(), e);
        }
    }

    public int deleteExpired(long nowMs) {
        String sql = "DELETE FROM links WHERE expires_at_ms <= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, nowMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB cleanup error: " + e.getMessage(), e);
        }
    }

    // АТОМАРНО: +1 к кликам, блокировка при достижении лимита
    public Optional<Link> consumeClick(String code, long nowMs) {
        String update =
            """
        UPDATE links
           SET clicks = clicks + 1,
               active = CASE WHEN clicks + 1 >= max_clicks THEN 0 ELSE 1 END
         WHERE code = ?
           AND active = 1
           AND expires_at_ms > ?
           AND clicks < max_clicks
        """;

        try (Connection c = conn()) {
            c.setAutoCommit(false);

            int updated;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, code);
                ps.setLong(2, nowMs);
                updated = ps.executeUpdate();
            }

            if (updated == 0) {
                c.rollback();
                return Optional.empty();
            }

            Optional<Link> link = findByCodeTx(c, code);
            c.commit();
            return link;
        } catch (SQLException e) {
            throw new RuntimeException("DB consume error: " + e.getMessage(), e);
        }
    }

    private Optional<Link> findByCodeTx(Connection c, String code) throws SQLException {
        String sql = "SELECT * FROM links WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    private Link map(ResultSet rs) throws SQLException {
        Link l = new Link();
        l.id = rs.getLong("id");
        l.userUuid = rs.getString("user_uuid");
        l.code = rs.getString("code");
        l.originalUrl = rs.getString("original_url");
        l.createdAtMs = rs.getLong("created_at_ms");
        l.expiresAtMs = rs.getLong("expires_at_ms");
        l.maxClicks = rs.getInt("max_clicks");
        l.clicks = rs.getInt("clicks");
        l.active = rs.getInt("active");
        return l;
    }
}
