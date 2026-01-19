package common;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private String type;
    private String playerId;
    private String playerName;
    private double x;
    private double y;
    private String targetColor;
    private int round;
    private double timeLeft;
    private double duration;
    private boolean gameStarted;
    private boolean isRoundActive;
    private String winner;
    private List<ScoreboardEntry> scores;
    private List<Player> players;
    private double matchStartCountdown;
    private byte[] field;
    private String reason;

    // Пустой конструктор для Gson
    public Message() {
    }

    public Message(String type) {
        this.type = type;
    }

    // Геттеры и сеттеры
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getTargetColor() {
        return targetColor;
    }

    public void setTargetColor(String targetColor) {
        this.targetColor = targetColor;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public double getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(double timeLeft) {
        this.timeLeft = timeLeft;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public boolean isIsRoundActive() {
        return isRoundActive;
    }

    public void setIsRoundActive(boolean isRoundActive) {
        this.isRoundActive = isRoundActive;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public List<ScoreboardEntry> getScores() {
        if (scores == null) {
            scores = new ArrayList<>();
        }
        return scores;
    }

    public void setScores(List<ScoreboardEntry> scores) {
        this.scores = scores;
    }


    public List<Player> getPlayers() {
        if (players == null) {
            players = new ArrayList<>();
        }
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public double getMatchStartCountdown() {
        return matchStartCountdown;
    }

    public void setMatchStartCountdown(double matchStartCountdown) {
        this.matchStartCountdown = matchStartCountdown;
    }

    public byte[] getField() {
        return field;
    }

    public void setField(byte[] field) {
        this.field = field;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    // Сериализация/десериализация
    private static final Gson gson = new Gson();

    public static Message fromJson(String json) {
        return gson.fromJson(json, Message.class);
    }

    public String toJson() {
        return gson.toJson(this);
    }


}