package client.screens;

import client.MainApp;
import client.NetworkService;
import common.*;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameScreen extends BorderPane {

    private final MainApp app;
    private final NetworkService networkService;

    // Игровой холст
    private final Canvas gameCanvas;
    private final GraphicsContext gc;

    // Компас
    private final Canvas compassCanvas;
    private final GraphicsContext compassGc;
    private double compassAngle = 0; // угол стрелки компаса
    private double playerAngle = 0; // направление движения игрока
    private boolean showCompass = true;

    // UI
    private final Label roundLabel;
    private final Label timerLabel;
    private final Label colorLabel;
    private final Label statusLabel;
    private final Label playersLabel;

    // Счетчик матча
    private double matchStartCountdown = 0;
    private boolean isMatchStarting = false;
    private StackPane fullScreenCountdown;
    private Label countdownLabel;

    // Кнопка выхода
    private final Button exitButton;

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
    private byte[] field; // GRID_W * GRID_H

    // Таймеры
    private Timeline countdownAnimation;
    private Timeline fullScreenCountdownTimeline;

    // Новое: для компаса к целевому цвету
    private double targetDirectionAngle = 0;
    private boolean hasValidDirection = false;
    private Timeline compassAnimation;

    public GameScreen(MainApp app, NetworkService networkService) {
        this.app = app;
        this.networkService = networkService;

        setPadding(new Insets(10));

        gameCanvas = new Canvas(GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
        gc = gameCanvas.getGraphicsContext2D();

        compassCanvas = new Canvas(100, 100);
        compassGc = compassCanvas.getGraphicsContext2D();

        HBox topPanel = new HBox(15);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0;");

        roundLabel = new Label("Раунд: 0");
        roundLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        playersLabel = new Label("Игроков: 1");
        playersLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        topPanel.getChildren().add(playersLabel);


        timerLabel = new Label("Время: 0.0");
        timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        timerLabel.setTextFill(Color.RED);

        colorLabel = new Label("Цвет: -");
        colorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        exitButton = new Button("Выйти из игры");
        exitButton.setOnAction(e -> handleExitGame());
        exitButton.setStyle("-fx-font-weight: bold; -fx-background-color: #e74c3c; -fx-text-fill: white;");

        topPanel.getChildren().addAll(roundLabel, timerLabel, colorLabel, exitButton);

        statusLabel = new Label("Ожидание начала матча...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        VBox bottomPanel = new VBox(statusLabel);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(10));

        BorderPane centerPane = new BorderPane();
        centerPane.setCenter(gameCanvas);
        centerPane.setTop(compassCanvas);
        BorderPane.setAlignment(compassCanvas, Pos.TOP_RIGHT);
        BorderPane.setMargin(compassCanvas, new Insets(10));

        // Создаем контейнер для полноэкранного счетчика
        fullScreenCountdown = new StackPane();
        fullScreenCountdown.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-alignment: center;");
        fullScreenCountdown.setVisible(false);

        countdownLabel = new Label();
        countdownLabel.setFont(Font.font("Arial", FontWeight.BOLD, 120));
        countdownLabel.setTextFill(Color.WHITE);
        fullScreenCountdown.getChildren().add(countdownLabel);

        // Добавляем полноэкранный счетчик поверх игрового поля
        StackPane rootStack = new StackPane();
        rootStack.getChildren().addAll(centerPane, fullScreenCountdown);

        setTop(topPanel);
        setCenter(rootStack);
        setBottom(bottomPanel);

        gameCanvas.setFocusTraversable(true);
        gameCanvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
                newScene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));
            }
        });


        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateGame();
                renderGame();
            }
        }.start();

        gameCanvas.requestFocus();

        // Запускаем анимацию компаса
        startCompassAnimation();
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

    /**
     * Находит ближайшее пятно целевого цвета и обновляет направление компаса
     */
    private void updateTargetDirection() {
        if (field == null || currentTargetColor == null || currentTargetColor.isEmpty() || !isRoundActive) {
            hasValidDirection = false;
            return;
        }

        double closestDistance = Double.MAX_VALUE;
        double targetX = playerX;
        double targetY = playerY;

        // Ищем ближайшее пятно с целевым цветом
        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                int idx = field[y * GameSettings.GRID_W + x];
                String spotColor = GameSettings.ROUND_COLORS[idx];

                if (spotColor.equals(currentTargetColor)) {
                    // Центр пятна в пиксельных координатах
                    double spotCenterX = x * GameSettings.CELL_SIZE + GameSettings.CELL_SIZE / 2.0;
                    double spotCenterY = y * GameSettings.CELL_SIZE + GameSettings.CELL_SIZE / 2.0;

                    // Вычисляем расстояние от игрока до центра пятна
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

        // Вычисляем угол от игрока к ближайшему пятну целевого цвета
        double dx = targetX - playerX;
        double dy = targetY - playerY;
        targetDirectionAngle = Math.atan2(dy, dx);
        hasValidDirection = true;
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
            // Отображаем счетчик матча вместо раунда
            roundLabel.setText("Старт через: " + String.format("%.1f", matchStartCountdown));
            statusLabel.setText("Ожидание начала матча...");
            statusLabel.setTextFill(Color.DARKBLUE);
        } else if (gameStarted) {
            roundLabel.setText("Раунд: " + currentRound);
            if (isRoundActive) {
                timerLabel.setText(String.format("Время: %.1f", roundTimeLeft));
                timerLabel.setTextFill(Color.RED);
                statusLabel.setText("Встаньте на " + currentTargetColor);
                statusLabel.setTextFill(Color.DARKBLUE);
            } else {
                statusLabel.setText(isAlive ? "Вы выжили!" : "Вы проиграли!");
                statusLabel.setTextFill(isAlive ? Color.DARKGREEN : Color.DARKRED);
            }
        }

        if (currentTargetColor != null && !currentTargetColor.isEmpty()) {
            colorLabel.setText("Цвет: " + currentTargetColor);
            try {
                colorLabel.setTextFill(Color.web(currentTargetColor));
            } catch (Exception e) {
                colorLabel.setTextFill(Color.DARKBLUE);
            }
        }

        // После обновления данных обновляем направление к целевому цвету
        if (gameStarted && isRoundActive && field != null && currentTargetColor != null && !currentTargetColor.isEmpty() && showCompass) {
            updateTargetDirection();
        }
    }

    private void drawCompass() {
        if (!showCompass) return;
        double w = compassCanvas.getWidth();
        double h = compassCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double r = w / 2 - 10;

        // Очищаем холст
        compassGc.clearRect(0, 0, w, h);

        // Рисуем фон компаса
        compassGc.setFill(Color.rgb(40, 40, 50, 0.784)); // темно-синий полупрозрачный фон
        compassGc.fillOval(5, 5, w - 10, h - 10);

        // Рисуем обводку компаса
        compassGc.setStroke(Color.rgb(200, 200, 220));
        compassGc.setLineWidth(2);
        compassGc.strokeOval(5, 5, w - 10, h - 10);

        // Рисуем северную метку
        compassGc.setFill(Color.WHITE);
        compassGc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        compassGc.fillText("N", cx - 3, 20);

        // Рисуем деления на компасе
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

        // Плавное движение стрелки к целевому направлению
        if (hasValidDirection && gameStarted && isRoundActive) {
            compassAngle = smoothAngle(compassAngle, targetDirectionAngle, 0.15);
        } else {
            // Если нет направления, плавно возвращаем к текущему направлению движения
            compassAngle = smoothAngle(compassAngle, playerAngle, 0.05);
        }

        // Определяем цвет стрелки в зависимости от состояния
        Color arrowColor;
        if (!gameStarted || !isRoundActive) {
            arrowColor = Color.GRAY; // Серый когда раунд не активен
        } else if (!hasValidDirection) {
            arrowColor = Color.YELLOW; // Желтый если нет информации
        } else {
            // Используем цвет текущего раунда, если он валидный
            try {
                arrowColor = Color.web(currentTargetColor);
            } catch (Exception e) {
                arrowColor = Color.CYAN; // Голубой как резервный цвет
            }
        }

        // Рисуем стрелку
        double arrowLength = r - 5;
        double arrowHeadSize = 10;

        // Основная линия стрелки
        double x = cx + Math.cos(compassAngle) * arrowLength;
        double y = cy + Math.sin(compassAngle) * arrowLength;

        compassGc.setStroke(arrowColor);
        compassGc.setLineWidth(3);
        compassGc.strokeLine(cx, cy, x, y);

        // Треугольник на конце стрелки
        double arrowAngle1 = compassAngle + Math.PI * 0.8;
        double arrowAngle2 = compassAngle - Math.PI * 0.8;

        double x1 = x + Math.cos(arrowAngle1) * arrowHeadSize;
        double y1 = y + Math.sin(arrowAngle1) * arrowHeadSize;
        double x2 = x + Math.cos(arrowAngle2) * arrowHeadSize;
        double y2 = y + Math.sin(arrowAngle2) * arrowHeadSize;

        compassGc.setFill(arrowColor);
        compassGc.fillPolygon(new double[]{x, x1, x2}, new double[]{y, y1, y2}, 3);

        // Рисуем центр компаса
        compassGc.setFill(Color.WHITE);
        compassGc.fillOval(cx - 4, cy - 4, 8, 8);
        compassGc.setStroke(Color.BLACK);
        compassGc.strokeOval(cx - 4, cy - 4, 8, 8);

        // Показываем расстояние до цели
        if (hasValidDirection && gameStarted && isRoundActive) {
            double dx = Math.cos(targetDirectionAngle) * 100;
            double dy = Math.sin(targetDirectionAngle) * 100;
            double distance = Math.sqrt(dx * dx + dy * dy);

            compassGc.setFill(arrowColor);
            compassGc.setFont(Font.font("Arial", FontWeight.BOLD, 8));
            // Показываем только когда компас активен и не закрыт другими элементами UI
        }
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

        fullScreenCountdownTimeline.setCycleCount(startValue + 1); // 3, 2, 1, Start!
        fullScreenCountdownTimeline.setOnFinished(e -> {
            fullScreenCountdown.setVisible(false);
        });

        fullScreenCountdownTimeline.play();
    }

    private void updateGame() {
        if (!gameStarted || !isRoundActive || !isAlive || isMatchStarting) return;

        double dx = 0;
        double dy = 0;

        if (pressedKeys.contains(KeyCode.W) || pressedKeys.contains(KeyCode.UP)) dy -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN)) dy += GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT)) dx -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT)) dx += GameSettings.MOVE_SPEED;

        if (dx != 0 || dy != 0) {
            double targetAngle = Math.atan2(dy, dx);
            // Сглаживание: двигаем compassAngle к targetAngle на 0.1 радиана за тик
            compassAngle = smoothAngle(compassAngle, targetAngle, 0.1);
        }

        playerX = Math.max(10, Math.min(playerX + dx, GameSettings.WORLD_WIDTH - 10));
        playerY = Math.max(10, Math.min(playerY + dy, GameSettings.WORLD_HEIGHT - 10));

        networkService.sendMove(playerX, playerY);
    }

    private void renderGame() {
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        drawSpots();
        drawPlayers();
        drawCompass();

        gc.setStroke(Color.BLACK);
        gc.strokeRect(0, 0, GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
    }

    private void drawSpots() {
        int w = GameSettings.GRID_W;

        // Если поля нет — всё серое
        if (field == null) {
            drawAllGray();
            return;
        }

        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                int idx = field[y * w + x];
                String cellColor = GameSettings.ROUND_COLORS[idx];

                // Если раунд не активен — показываем только текущий цвет
                if (!isRoundActive && !cellColor.equalsIgnoreCase(currentTargetColor)) {
                    gc.setFill(Color.GRAY);
                } else {
                    gc.setFill(Color.web(cellColor));
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
        gc.setFill(Color.GRAY);
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
        // Рисуем других игроков
        for (Player p : players.values()) {
            gc.setFill(p.isAlive() ? Color.RED : Color.GRAY);
            double x = p.getX() - 10;
            double y = p.getY() - 10;
            gc.fillOval(x, y, 20, 20);

            // Контур
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(x, y, 20, 20);
        }

        // Рисуем себя
        gc.setFill(isAlive ? Color.BLUE : Color.GRAY);
        double px = playerX - 10;
        double py = playerY - 10;
        gc.fillOval(px, py, 20, 20);

        // Контур себя
        gc.setStroke(Color.CYAN); // или Color.BLACK, если хочешь одинаково
        gc.setLineWidth(2);
        gc.strokeOval(px, py, 20, 20);
    }

    // Метод для плавного приближения угла
    private double smoothAngle(double current, double target, double maxStep) {
        double diff = target - current;

        // корректировка разницы в диапазон [-PI, PI]
        while (diff < -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        if (Math.abs(diff) <= maxStep) {
            return target; // достигли цели
        } else {
            return current + Math.signum(diff) * maxStep;
        }
    }

    private void handleExitGame() {
        // Показываем диалог подтверждения
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение выхода");
        alert.setHeaderText("Вы уверены, что хотите выйти из игры?");
        alert.setContentText("Все текущие прогресс будет потерян.");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                // Отключаемся от сервера
                if (networkService != null && networkService.isConnected()) {
                    networkService.disconnect();
                }

                // Показываем экран подключения
                app.showConnectionScreen();
            }
        });
    }

    public void cleanup() {
        // Останавливаем все анимации
        if (countdownAnimation != null) {
            countdownAnimation.stop();
            countdownAnimation = null;
        }

        if (fullScreenCountdownTimeline != null) {
            fullScreenCountdownTimeline.stop();
            fullScreenCountdownTimeline = null;
        }
        if (compassAnimation != null) {
            compassAnimation.stop();
            compassAnimation = null;
        }

        gameCanvas.setOnKeyPressed(null);
        gameCanvas.setOnKeyReleased(null);
        pressedKeys.clear();
    }

    public void handleRoundStart(Message message) {
        currentTargetColor = message.getTargetColor();
        roundDuration = message.getDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;
        field = message.getField();
        statusLabel.setText("Играем");
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public void setShowCompass(boolean showCompass) {
        this.showCompass = showCompass;
    }
}