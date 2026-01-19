package server;

import common.*;
import server.db.ScoreboardRepository;

import java.util.*;
import java.util.concurrent.*;

public class GameRoom {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ScoreboardRepository scoreboard;

    // Состояние игры
    private int round = 0;
    private double roundTimeLeft;
    private double roundDuration;
    private String currentTargetColor;
    private boolean isRoundActive = false;
    private boolean gameStarted = false;
    private double matchStartCountdown = GameSettings.BASE_MATCH_START_DELAY;
    private byte[] field; // GRID_W * GRID_H

    // Таймеры
    private ScheduledFuture<?> roundTimer;
    private ScheduledFuture<?> matchStartTimer;

    // Для рассылки обновлений
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public GameRoom(ScoreboardRepository scoreboard) {
        this.scoreboard = scoreboard;
        generateField();
    }

    // Регистрация клиента для рассылки обновлений
    public void registerClient(ClientHandler client) {
        clients.add(client);
        System.out.println("[ROOM] Зарегистрирован клиент для обновлений. Всего клиентов: " + clients.size());
    }

    public void unregisterClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("[ROOM] Удален клиент из обновлений. Всего клиентов: " + clients.size());
    }


    public synchronized void addPlayer(Player player) {
        if (gameStarted) {
            return;
        }
        players.put(player.getId(), player);
        System.out.println("[ROOM] Добавлен игрок: " + player.getName() + " (ID: " + player.getId() + ")");
        System.out.println("[ROOM] Всего игроков: " + players.size());

        // Если набралось достаточно игроков и игра еще не начата
        if (players.size() >= 2 && !gameStarted) {
            startMatchCountdown();
        }

        // Отправляем обновление всем игрокам
        broadcastGameState();
    }

    public synchronized void removePlayer(String playerId) {
        // Сначала удаляем игрока из карты и получаем его
        Player player = players.remove(playerId);

        // Проверяем, что игрок существует
        if (player == null) {
            System.out.println("[ROOM] Игрок с ID " + playerId + " не найден для удаления");
            return;
        }

        String name = player.getName();
        int roundPlayer = Math.max(0, round - 1);
        scoreboard.updateIfBetter(name, roundPlayer);

        System.out.println("[ROOM] Удален игрок: " + name);

        // Если во время игры остался только один игрок
        if (gameStarted && players.size() < 2) {
            endGame(null);
        }

        // Отправляем обновление всем игрокам
        broadcastGameState();
    }

    private void startMatchCountdown() {
        if (matchStartTimer != null && !matchStartTimer.isDone()) {
            matchStartTimer.cancel(true);
        }

        matchStartCountdown = calculateMatchStartDelay();

        // Гарантируем положительное значение
        if (matchStartCountdown < 0) {
            matchStartCountdown = GameSettings.MIN_MATCH_START_DELAY;
        }

        gameStarted = false;

        System.out.println("[ROOM] Запуск обратного отсчета до начала матча: " +
                String.format("%.1f", matchStartCountdown) + " сек");

        matchStartTimer = scheduler.scheduleAtFixedRate(() -> {
            matchStartCountdown -= 0.1;

            // Если игроков меньше 2, приостанавливаем отсчет
            if (players.size() < 2) {
                matchStartCountdown += 0.1;
            }

            // Если отсчет дошел до нуля и игроков достаточно - начинаем игру
            if (matchStartCountdown <= 0 && players.size() >= 2) {
                startGame();
                if (matchStartTimer != null) matchStartTimer.cancel(true);
            } else {
                broadcastGameState();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private double calculateMatchStartDelay() {
        // Если это первый вызов после сброса, используем базовое значение
        if (matchStartCountdown == GameSettings.BASE_MATCH_START_DELAY) {
            double delay = GameSettings.BASE_MATCH_START_DELAY -
                    ((players.size() - 2) * GameSettings.PLAYER_DELAY_REDUCTION);
            return Math.max(delay, GameSettings.MIN_MATCH_START_DELAY);
        }

        // В противном случае используем текущее значение (для динамического изменения)
        double delay = matchStartCountdown -
                ((players.size() - 2) * GameSettings.PLAYER_DELAY_REDUCTION);
        return Math.max(delay, GameSettings.MIN_MATCH_START_DELAY);
    }

    private void startGame() {
        isRoundActive = false;
        currentTargetColor = "#FFFFF";
        gameStarted = true;
        System.out.println("[ROOM] Игра началась! Всего игроков: " + players.size());
        startNewRound(true);
    }

    private void startNewRound(boolean isStart) {
        round++;
        currentTargetColor = GameSettings.ROUND_COLORS[random.nextInt(GameSettings.ROUND_COLORS.length)];
        roundDuration = calculateRoundDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;
        generateField();


        System.out.println("[ROOM] Раунд " + round + " начался. Цвет: " + currentTargetColor +
                ". Время: " + String.format("%.1f", roundDuration) + " сек" +
                ". Время: " + String.format("%.1f", matchStartCountdown) + " сек"
        );

        // Отправляем уведомление о начале раунда
        if (isStart) {
            broadcastGameStart();
        } else {
            broadcastRoundStart();
        }

        // Запуск таймера раунда
        if (roundTimer != null && !roundTimer.isDone()) {
            roundTimer.cancel(true);
        }

        roundTimer = scheduler.scheduleAtFixedRate(() -> {
            roundTimeLeft -= 0.1;

            if (roundTimeLeft <= 0 || players.size() < 2) {
                endRound();
                if (roundTimer != null) roundTimer.cancel(true);
            } else {
                broadcastGameState();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private double calculateRoundDuration() {
        double duration = GameSettings.INITIAL_ROUND_TIME - ((round - 1) * GameSettings.ROUND_TIME_DECREMENT);
        return Math.max(duration, GameSettings.MIN_ROUND_TIME);
    }

    private void endRound() {
        isRoundActive = false;
        System.out.println("[ROOM] Раунд " + round + " завершен");

        List<Player> survivors = new ArrayList<>();
        List<String> eliminatedPlayers = new ArrayList<>();

        for (Player player : players.values()) {
            if (player.isAlive()) {
                String spotColor = getSpotColorAt(player.getX(), player.getY());
                if (spotColor.equals(currentTargetColor)) {
                    survivors.add(player);
                    System.out.println("[ROOM] Игрок выжил: " + player.getName());
                } else {
                    player.setAlive(false);
                    eliminatedPlayers.add(player.getId());
                    System.out.println("[ROOM] Игрок выбыл: " + player.getName() +
                            " (стоял на " + spotColor + ", нужен " + currentTargetColor + ")");
                }
            }
        }

        broadcastGameState();

        // Отправляем персональные сообщения eliminated игрокам
        for (String playerId : eliminatedPlayers) {
            sendPlayerEliminated(playerId);
        }

        // Задержка перед следующим раундом или завершением
        scheduler.schedule(() -> {
            if (survivors.size() <= 1) {
                Player winner = survivors.isEmpty() ? null : survivors.get(0);
                endGame(winner);
            } else {
                startNewRound(false);
            }
        }, 2000, TimeUnit.MILLISECONDS);
    }

    private void sendPlayerEliminated(String playerId) {
        ClientHandler handler = getClientHandlerByPlayerId(playerId);
        if (handler != null) {
            Message msg = new Message(MessageTypes.PLAYER_ELIMINATED);
            msg.setWinner("Вы проиграли!");

            // Добавляем текущий scoreboard для выбывшего игрока
            List<ScoreboardEntry> topScores = scoreboard.getTop(10);
            msg.setScores(topScores);

            handler.sendMessage(msg);
        }
    }

    // Вспомогательный метод для получения ClientHandler по playerId
    private ClientHandler getClientHandlerByPlayerId(String playerId) {
        for (ClientHandler handler : clients) {
            if (playerId.equals(handler.getPlayerId())) {
                return handler;
            }
        }
        return null;
    }

    private void endGame(Player winner) {
        if (winner != null) {
            System.out.println("[ROOM] Игра завершена. Победитель: " + winner.getName());

            // score = количество раундов
            scoreboard.updateIfBetter(winner.getName(), round);
        } else {
            System.out.println("[ROOM] Игра завершена. Ничья.");
        }

        broadcastGameOver(winner);
        resetParamsGame();
    }


    public void resetParamsGame() {
        // Отменяем все таймеры
        if (roundTimer != null && !roundTimer.isDone()) {
            roundTimer.cancel(true);
        }
        if (matchStartTimer != null && !matchStartTimer.isDone()) {
            matchStartTimer.cancel(true);
        }

        // Сбрасываем параметры игры
        gameStarted = false;
        isRoundActive = false;
        currentTargetColor = "#FFFFFF";
        round = 0;
        roundDuration = GameSettings.INITIAL_ROUND_TIME;
        matchStartCountdown = GameSettings.BASE_MATCH_START_DELAY; // Начальное значение из настроек

        System.out.println("[ROOM] Сброс комнаты");

        // Создаем копию ключей для безопасного удаления
        List<String> playerIds = new ArrayList<>(players.keySet());
        for (String playerId : playerIds) {
            removePlayer(playerId);
        }

        // Генерируем новое поле для следующей игры
        generateField();

        // Отправляем обновление состояния
        broadcastGameState();
    }

    private String getSpotColorAt(double x, double y) {
        int gx = (int) (x / GameSettings.CELL_SIZE);
        int gy = (int) (y / GameSettings.CELL_SIZE);

        gx = Math.max(0, Math.min(gx, GameSettings.GRID_W - 1));
        gy = Math.max(0, Math.min(gy, GameSettings.GRID_H - 1));

        int index = field[gy * GameSettings.GRID_W + gx];
        return GameSettings.ROUND_COLORS[index];
    }


    public void handlePlayerMove(String playerId, double x, double y) {
        Player player = players.get(playerId);
        if (player != null && player.isAlive()) {
            // Ограничение движения в пределах поля
            double boundedX = Math.max(10, Math.min(x, GameSettings.WORLD_WIDTH - 10));
            double boundedY = Math.max(10, Math.min(y, GameSettings.WORLD_HEIGHT - 10));
            player.setX(boundedX);
            player.setY(boundedY);
            broadcastGameState();
        }
    }

    // Рассылка обновлений всем клиентам
    private void broadcastMessage(Message message) {
        for (ClientHandler client : new ArrayList<>(clients)) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("[ROOM][ERROR] Ошибка отправки сообщения клиенту: " + e.getMessage());
                clients.remove(client);
            }
        }
    }

    private void broadcastGameState() {
        Message msg = new Message(MessageTypes.GAME_STATE);
        msg.setRound(round);
        msg.setTargetColor(currentTargetColor);
        msg.setTimeLeft(roundTimeLeft);
        msg.setDuration(roundDuration);
        msg.setGameStarted(gameStarted);
        msg.setIsRoundActive(isRoundActive);
        msg.setMatchStartCountdown(matchStartCountdown);
        msg.setField(field);

        // Передаем клонов для потокобезопасности
        List<Player> playerList = new ArrayList<>();
        for (Player player : players.values()) {
            playerList.add(player.clone());
        }
        msg.setPlayers(playerList);


        broadcastMessage(msg);
    }

    private void broadcastRoundStart() {
        Message msg = new Message(MessageTypes.ROUND_START);
        msg.setTargetColor(currentTargetColor);
        msg.setDuration(roundDuration);
        msg.setField(field);
        broadcastMessage(msg);
    }

    private void broadcastGameStart() {
        generateField();
        Message msg = new Message(MessageTypes.MATCH_START);
        System.out.println("[ROOM] MATCH__START");
        msg.setTargetColor(currentTargetColor);
        msg.setDuration(roundDuration);
        msg.setField(field);
        broadcastMessage(msg);
    }

    private void broadcastGameOver(Player winner) {
        Message msg = new Message(MessageTypes.GAME_OVER);

        if (winner != null) {
            msg.setWinner(winner.getName());
        }

        // Получаем ТОП-10 из SQLite
        List<ScoreboardEntry> topScores = scoreboard.getTop(10);

        msg.setScores(topScores);
        broadcastMessage(msg);
    }


    private void generateField() {
        int w = GameSettings.GRID_W;
        int h = GameSettings.GRID_H;
        field = new byte[w * h];

        // Инициализируем поле базовым цветом (например, первым цветом)
        byte baseColor = 0;
        for (int i = 0; i < w * h; i++) {
            field[i] = baseColor;
        }

        Random r = new Random();
        int numColors = GameSettings.ROUND_COLORS.length;

        // 1. Гарантированное размещение каждого цвета
        for (byte colorIndex = 0; colorIndex < numColors; colorIndex++) {
            // Размещаем минимум 3 пятна для каждого цвета
            for (int blob = 0; blob < 3; blob++) {
                int cx = r.nextInt(w);
                int cy = r.nextInt(h);
                int radius = 2 + r.nextInt(3); // Небольшие пятна для гарантированного размещения

                for (int y = -radius; y <= radius; y++) {
                    for (int x = -radius; x <= radius; x++) {
                        int nx = cx + x;
                        int ny = cy + y;

                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;

                        double dist = Math.sqrt(x * x + y * y);
                        if (dist <= radius) {
                            field[ny * w + nx] = colorIndex;
                        }
                    }
                }
            }
        }

        // 2. Добавляем случайные крупные пятна для разнообразия
        int blobs = 8 + r.nextInt(12);
        for (int i = 0; i < blobs; i++) {
            int cx = r.nextInt(w);
            int cy = r.nextInt(h);
            int radius = 4 + r.nextInt(8); // Крупные пятна
            byte colorIndex = (byte) r.nextInt(numColors);

            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    int nx = cx + x;
                    int ny = cy + y;

                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;

                    double dist = Math.sqrt(x * x + y * y);
                    if (dist <= radius * 0.8) { // Используем эллипс для более естественных форм
                        field[ny * w + nx] = colorIndex;
                    }
                }
            }
        }

        // 3. Проверка и гарантия наличия всех цветов
        boolean[] colorsPresent = new boolean[numColors];
        for (int i = 0; i < w * h; i++) {
            colorsPresent[field[i]] = true;
        }

        // Если какой-то цвет отсутствует - добавляем его принудительно
        for (byte colorIndex = 0; colorIndex < numColors; colorIndex++) {
            if (!colorsPresent[colorIndex]) {
                int cx = r.nextInt(w);
                int cy = r.nextInt(h);
                field[cy * w + cx] = colorIndex;
            }
        }

        // 4. Добавляем шум для естественности
        int noisePoints = w * h / 20; // 5% ячеек
        for (int i = 0; i < noisePoints; i++) {
            int x = r.nextInt(w);
            int y = r.nextInt(h);
            byte randomColor = (byte) r.nextInt(numColors);
            field[y * w + x] = randomColor;
        }
    }

    public boolean isGameStarted() {
        return gameStarted;
    }
}