package server.db;

import common.ScoreboardEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ScoreboardRepository {

    private static final String DB_URL = "jdbc:sqlite:scoreboard.db";

    public ScoreboardRepository() {
        init();
    }

    private void init() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS scoreboard (
                            player_name TEXT PRIMARY KEY,
                            score INTEGER NOT NULL
                        )
                    """);

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка инициализации БД", e);
        }
    }

    /**
     * Обновить результат, ТОЛЬКО если он лучше предыдущего
     */
    public void updateIfBetter(String playerName, int newScore) {
        if (newScore < 5) return;
        String sql = """
                    INSERT INTO scoreboard (player_name, score)
                    VALUES (?, ?)
                    ON CONFLICT(player_name)
                    DO UPDATE SET score = excluded.score
                    WHERE excluded.score > scoreboard.score
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerName);
            ps.setInt(2, newScore);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка обновления рейтинга", e);
        }
    }

    /**
     * Получить ТОП N игроков
     */
    public List<ScoreboardEntry> getTop(int limit) {
        String sql = """
                    SELECT player_name, score
                    FROM scoreboard
                    ORDER BY score DESC
                    LIMIT ?
                """;

        List<ScoreboardEntry> result = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ScoreboardEntry(
                            rs.getString("player_name"),
                            rs.getInt("score")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка чтения рейтинга", e);
        }

        return result;
    }
}
