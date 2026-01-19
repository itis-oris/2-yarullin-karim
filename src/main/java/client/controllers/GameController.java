package client.controllers;

import client.MainApp;
import client.NetworkService;
import common.*;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameController {

    @FXML
    private Canvas gameCanvas;

    @FXML
    private Canvas compassCanvas;

    @FXML
    private Label roundLabel;

    @FXML
    private Label playersLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private Label colorLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button exitButton;

    @FXML
    private StackPane fullScreenCountdown;

    @FXML
    private Label countdownLabel;

    @FXML
    private StackPane rootStack;

    private GraphicsContext gc;
    private GraphicsContext compassGc;

    private MainApp app;
    private NetworkService networkService;

    private double compassAngle = 0;
    private double playerAngle = 0;
    private boolean showCompass = true;

    private double matchStartCountdown = 0;
    private boolean isMatchStarting = false;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Set<KeyCode> pressedKeys = ConcurrentHashMap.newKeySet();

    private String playerId;
    private int currentRound;
    private double roundTimeLeft;
    private double roundDuration;
    private String currentTargetColor = "";

    private boolean isRoundActive;
    private boolean gameStarted;
    private boolean isAlive = true;

    private double playerX = GameSettings.WORLD_WIDTH / 2;
    private double playerY = GameSettings.WORLD_HEIGHT / 2;
    private byte[] field;

    private Timeline fullScreenCountdownTimeline;
    private double targetDirectionAngle = 0;
    private boolean hasValidDirection = false;
    private Timeline compassAnimation;

    private AnimationTimer gameLoop;

    public GameController() {
        this.app = null;
        this.networkService = null;
    }

    @FXML
    private void initialize() {
        // Инициализация графических контекстов
        gc = gameCanvas.getGraphicsContext2D();
        compassGc = compassCanvas.getGraphicsContext2D();

        // Запуск игрового цикла
        startGameLoop();

        // Установка обработчиков клавиш
        setupKeyHandlers();

        // Запуск анимации компаса
        startCompassAnimation();
    }

    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateGame();
                renderGame();
            }
        };
        gameLoop.start();
    }

    private void setupKeyHandlers() {
        gameCanvas.setFocusTraversable(true);

        // Устанавливаем обработчики клавиш
        gameCanvas.setOnKeyPressed(e -> {
            pressedKeys.add(e.getCode());
            System.out.println("[KEY] Нажата клавиша: " + e.getCode() + ", Всего нажато: " + pressedKeys.size());
        });

        gameCanvas.setOnKeyReleased(e -> {
            pressedKeys.remove(e.getCode());
            System.out.println("[KEY] Отпущена клавиша: " + e.getCode() + ", Осталось нажато: " + pressedKeys.size());
        });

        // Добавляем обработчик потери фокуса
        gameCanvas.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                // При потере фокуса сбрасываем все нажатые клавиши
                if (!pressedKeys.isEmpty()) {
                    System.out.println("[KEY] Canvas потерян фокус, сброс " + pressedKeys.size() + " клавиш");
                    pressedKeys.clear();
                }
            } else {
                System.out.println("[KEY] Canvas получил фокус");
            }
        });

        // Устанавливаем фокус при загрузке
        Platform.runLater(() -> {
            gameCanvas.requestFocus();
            System.out.println("[KEY] Фокус установлен на gameCanvas после загрузки");
        });
    }

    public void setMainApp(MainApp mainApp) {
        this.app = mainApp;
    }

    public void setNetworkService(NetworkService networkService) {
        this.networkService = networkService;
    }

    public void setShowCompass(boolean showCompass) {
        this.showCompass = showCompass;
        compassCanvas.setVisible(showCompass);
        compassCanvas.setManaged(showCompass);
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public void updateGameState(Message message) {
        currentRound = message.getRound();
        roundTimeLeft = message.getTimeLeft();
        roundDuration = message.getDuration();
        currentTargetColor = message.getTargetColor();
        isRoundActive = message.isIsRoundActive();
        gameStarted = message.isGameStarted();
        matchStartCountdown = message.getMatchStartCountdown();

        if (message.getField() != null) {
            field = message.getField();
        }

        // Обработка счетчика матча
        if (matchStartCountdown > 0 && !gameStarted) {
            isMatchStarting = true;
        } else {
            isMatchStarting = false;
        }

        // Обработка полноэкранного счетчика при старте матча
        if (isMatchStarting && Math.abs(matchStartCountdown - 3.0) < 0.1 && !fullScreenCountdown.isVisible()) {
            showFullScreenCountdown(3);
        }

        players.clear();
        int count = 0;
        if (message.getPlayers() != null) {
            for (Player p : message.getPlayers()) {
                count++;
                if (!p.getId().equals(playerId)) {
                    players.put(p.getId(), p);
                } else {
                    playerX = p.getX();
                    playerY = p.getY();
                    isAlive = p.isAlive();
                }
            }
        }
        playersLabel.setText("Игроков: " + count);

        // Обновление UI в зависимости от состояния
        if (isMatchStarting) {
            roundLabel.setText("Старт через: " + String.format("%.1f", matchStartCountdown));
            statusLabel.setText("Ожидание начала матча...");
            statusLabel.setStyle("-fx-text-fill: #2980b9;");
        } else if (gameStarted) {
            roundLabel.setText("Раунд: " + currentRound);
            if (isRoundActive) {
                timerLabel.setText(String.format("Время: %.1f", roundTimeLeft));
                timerLabel.setStyle("-fx-text-fill: red;");
                statusLabel.setText("Встаньте на " + currentTargetColor);
                statusLabel.setStyle("-fx-text-fill: #2c3e50;");
            } else {
                statusLabel.setText(isAlive ? "Вы выжили!" : "Вы проиграли!");
                statusLabel.setStyle("-fx-text-fill: " + (isAlive ? "#27ae60" : "#e74c3c") + ";");
            }
        }

        if (currentTargetColor != null && !currentTargetColor.isEmpty()) {
            colorLabel.setText("Цвет: " + currentTargetColor);
            try {
                colorLabel.setStyle("-fx-text-fill: " + currentTargetColor + ";");
            } catch (Exception e) {
                colorLabel.setStyle("-fx-text-fill: #2c3e50;");
            }
        }

        // После обновления данных обновляем направление к целевому цвету
        if (gameStarted && isRoundActive && field != null && currentTargetColor != null && !currentTargetColor.isEmpty() && showCompass) {
            updateTargetDirection();
        }
    }

    private void updateTargetDirection() {
        if (field == null || currentTargetColor == null || currentTargetColor.isEmpty() || !isRoundActive) {
            hasValidDirection = false;
            return;
        }

        double closestDistance = Double.MAX_VALUE;
        double targetX = playerX;
        double targetY = playerY;

        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                int idx = field[y * GameSettings.GRID_W + x];
                String spotColor = GameSettings.ROUND_COLORS[idx];

                if (spotColor.equals(currentTargetColor)) {
                    double spotCenterX = x * GameSettings.CELL_SIZE + GameSettings.CELL_SIZE / 2.0;
                    double spotCenterY = y * GameSettings.CELL_SIZE + GameSettings.CELL_SIZE / 2.0;

                    double dx = spotCenterX - playerX;
                    double dy = spotCenterY - playerY;
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    if (distance < closestDistance) {
                        closestDistance = distance;
                        targetX = spotCenterX;
                        targetY = spotCenterY;
                    }
                }
            }
        }

        double dx = targetX - playerX;
        double dy = targetY - playerY;
        targetDirectionAngle = Math.atan2(dy, dx);
        hasValidDirection = true;
    }

    private void showFullScreenCountdown(int startValue) {
        fullScreenCountdown.setVisible(true);

        if (fullScreenCountdownTimeline != null) {
            fullScreenCountdownTimeline.stop();
        }

        final int[] currentValue = {startValue};
        countdownLabel.setText(String.valueOf(currentValue[0]));

        fullScreenCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            currentValue[0]--;
            if (currentValue[0] > 0) {
                countdownLabel.setText(String.valueOf(currentValue[0]));
            } else if (currentValue[0] == 0) {
                countdownLabel.setText("Start!");
            } else {
                fullScreenCountdown.setVisible(false);
                fullScreenCountdownTimeline.stop();
            }
        }));

        fullScreenCountdownTimeline.setCycleCount(startValue + 1);
        fullScreenCountdownTimeline.setOnFinished(e -> {
            fullScreenCountdown.setVisible(false);
        });

        fullScreenCountdownTimeline.play();
    }

    private void updateGame() {
        if (!gameStarted || !isRoundActive || !isAlive || isMatchStarting) {
            return;
        }

        double dx = 0, dy = 0;

        if (pressedKeys.contains(KeyCode.W) || pressedKeys.contains(KeyCode.UP)) dy -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN)) dy += GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT)) dx -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT)) dx += GameSettings.MOVE_SPEED;

        // Отладочная информация о движении
        if ((dx != 0 || dy != 0) && pressedKeys.size() == 0) {
            System.out.println("[DEBUG] ДВИЖЕНИЕ БЕЗ НАЖАТЫХ КЛАВИШ! Это ошибка состояния.");
        }

        if (dx != 0 || dy != 0) {
            double targetAngle = Math.atan2(dy, dx);
            compassAngle = smoothAngle(compassAngle, targetAngle, 0.1);
        }

        playerX = Math.max(10, Math.min(playerX + dx, GameSettings.WORLD_WIDTH - 10));
        playerY = Math.max(10, Math.min(playerY + dy, GameSettings.WORLD_HEIGHT - 10));

        if (dx != 0 || dy != 0) {
            networkService.sendMove(playerX, playerY);
        }
    }

    private void renderGame() {
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        drawSpots();
        drawPlayers();
        drawCompass();

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRect(0, 0, GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
    }

    private void drawSpots() {
        int w = GameSettings.GRID_W;

        if (field == null) {
            drawAllGray();
            return;
        }

        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                int idx = field[y * w + x];
                String cellColor = GameSettings.ROUND_COLORS[idx];

                if (!isRoundActive && !cellColor.equalsIgnoreCase(currentTargetColor)) {
                    gc.setFill(Color.GRAY);
                } else {
                    try {
                        gc.setFill(Color.web(cellColor));
                    } catch (Exception e) {
                        gc.setFill(Color.LIGHTGRAY);
                    }
                }

                gc.fillRect(
                        x * GameSettings.CELL_SIZE,
                        y * GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE
                );
            }
        }
    }

    private void drawAllGray() {
        gc.setFill(Color.LIGHTGRAY);
        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                gc.fillRect(
                        x * GameSettings.CELL_SIZE,
                        y * GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE
                );
            }
        }
    }

    private void drawPlayers() {
        for (Player p : players.values()) {
            gc.setFill(p.isAlive() ? Color.RED : Color.GRAY);
            double x = p.getX() - 10;
            double y = p.getY() - 10;
            gc.fillOval(x, y, 20, 20);

            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(x, y, 20, 20);
        }

        gc.setFill(isAlive ? Color.BLUE : Color.GRAY);
        double px = playerX - 10;
        double py = playerY - 10;
        gc.fillOval(px, py, 20, 20);

        gc.setStroke(Color.CYAN);
        gc.setLineWidth(2);
        gc.strokeOval(px, py, 20, 20);
    }

    private void drawCompass() {
        if (!showCompass) return;

        double w = compassCanvas.getWidth();
        double h = compassCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double r = w / 2 - 10;

        compassGc.clearRect(0, 0, w, h);

        // Фон компаса
        compassGc.setFill(Color.rgb(40, 40, 50, 0.784));
        compassGc.fillOval(5, 5, w - 10, h - 10);

        // Обводка
        compassGc.setStroke(Color.rgb(200, 200, 220));
        compassGc.setLineWidth(2);
        compassGc.strokeOval(5, 5, w - 10, h - 10);

        // Север
        compassGc.setFill(Color.WHITE);
        compassGc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        compassGc.fillText("N", cx - 3, 20);

        // Деления
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double innerX = cx + Math.cos(angle) * (r - 10);
            double innerY = cy + Math.sin(angle) * (r - 10);
            double outerX = cx + Math.cos(angle) * r;
            double outerY = cy + Math.sin(angle) * r;

            compassGc.setStroke(Color.rgb(180, 180, 200));
            compassGc.setLineWidth(1);
            compassGc.strokeLine(innerX, innerY, outerX, outerY);
        }

        // Плавное движение стрелки
        if (hasValidDirection && gameStarted && isRoundActive) {
            compassAngle = smoothAngle(compassAngle, targetDirectionAngle, 0.15);
        } else {
            compassAngle = smoothAngle(compassAngle, playerAngle, 0.05);
        }

        // Цвет стрелки
        Color arrowColor;
        if (!gameStarted || !isRoundActive) {
            arrowColor = Color.GRAY;
        } else if (!hasValidDirection) {
            arrowColor = Color.YELLOW;
        } else {
            try {
                arrowColor = Color.web(currentTargetColor);
            } catch (Exception e) {
                arrowColor = Color.CYAN;
            }
        }

        // Стрелка
        double arrowLength = r - 5;
        double arrowHeadSize = 10;
        double x = cx + Math.cos(compassAngle) * arrowLength;
        double y = cy + Math.sin(compassAngle) * arrowLength;

        compassGc.setStroke(arrowColor);
        compassGc.setLineWidth(3);
        compassGc.strokeLine(cx, cy, x, y);

        // Треугольник на конце
        double arrowAngle1 = compassAngle + Math.PI * 0.8;
        double arrowAngle2 = compassAngle - Math.PI * 0.8;
        double x1 = x + Math.cos(arrowAngle1) * arrowHeadSize;
        double y1 = y + Math.sin(arrowAngle1) * arrowHeadSize;
        double x2 = x + Math.cos(arrowAngle2) * arrowHeadSize;
        double y2 = y + Math.sin(arrowAngle2) * arrowHeadSize;

        compassGc.setFill(arrowColor);
        compassGc.fillPolygon(new double[]{x, x1, x2}, new double[]{y, y1, y2}, 3);

        // Центр
        compassGc.setFill(Color.WHITE);
        compassGc.fillOval(cx - 4, cy - 4, 8, 8);
        compassGc.setStroke(Color.BLACK);
        compassGc.strokeOval(cx - 4, cy - 4, 8, 8);
    }

    private double smoothAngle(double current, double target, double maxStep) {
        double diff = target - current;

        while (diff < -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        if (Math.abs(diff) <= maxStep) {
            return target;
        } else {
            return current + Math.signum(diff) * maxStep;
        }
    }

    private void startCompassAnimation() {
        compassAnimation = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            if (gameStarted && isRoundActive && field != null && currentTargetColor != null && !currentTargetColor.isEmpty()) {
                updateTargetDirection();
            }
        }));
        compassAnimation.setCycleCount(Timeline.INDEFINITE);
        compassAnimation.play();
    }

    @FXML
    private void handleExitGame() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение выхода");
        alert.setHeaderText("Вы уверены, что хотите выйти из игры?");
        alert.setContentText("Все текущие прогресс будет потерян.");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                if (networkService != null && networkService.isConnected()) {
                    networkService.disconnect();
                }
                app.showConnectionScreen();
            }
        });
    }

    public void handleRoundStart(Message message) {
        currentTargetColor = message.getTargetColor();
        roundDuration = message.getDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;
        field = message.getField();
        statusLabel.setText("Играем");
        statusLabel.setStyle("-fx-text-fill: #2c3e50;");
    }

    public void cleanup() {
        if (gameLoop != null) {
            gameLoop.stop();
        }

        if (fullScreenCountdownTimeline != null) {
            fullScreenCountdownTimeline.stop();
            fullScreenCountdownTimeline = null;
        }

        if (compassAnimation != null) {
            compassAnimation.stop();
            compassAnimation = null;
        }

        // Важно: очищаем состояние клавиш при выходе из игры
        pressedKeys.clear();
    }

    // Добавьте этот метод в класс GameController
    public void requestFocusOnGameCanvas() {
        if (gameCanvas != null) {
            gameCanvas.requestFocus();
            System.out.println("[GAME] Фокус установлен на игровом поле");
        }
    }
}