package server;

import common.Message;
import common.MessageTypes;
import common.Player;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameRoom gameRoom;
    private String playerId;
    private String playerName;
    private boolean running = true;

    private OutputStream outputStream;
    private InputStream inputStream;

    public ClientHandler(Socket socket, GameRoom gameRoom) {
        this.socket = socket;
        this.gameRoom = gameRoom;
    }

    private void handleIncomingMessage(Message message) {
        switch (message.getType()) {
            case MessageTypes.CONNECT:
                handleConnect(message);
                break;
                
            case MessageTypes.MOVE:
                handleMove(message);
                break;
            case MessageTypes.DISCONNECT:
                disconnect(); // Просто останавливаем цикл обработки
                break;
        }
    }

    @Override
    public void run() {
        System.out.println("[SERVER][DEBUG] Начало обработки клиента: " + socket.getInetAddress());
        try {
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            System.out.println("[SERVER][DEBUG] Потоки ввода/вывода созданы");
            gameRoom.registerClient(this);
            processMessages();
        } catch (Exception e) {
            System.err.println("[SERVER][ERROR] ОШИБКА: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Удаляем игрока только если он был добавлен
            if (playerId != null && gameRoom != null) {
                gameRoom.removePlayer(playerId);
            }

            // Отменяем регистрацию клиента
            gameRoom.unregisterClient(this);

            // Закрываем сокет
            try {
                if (socket != null && !socket.isClosed()) {
                    System.out.println("[SERVER][DEBUG] Закрытие сокета");
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии сокета: " + e.getMessage());
            }

            System.out.println("[SERVER][DEBUG] Клиент окончательно отключен: " + playerId);
        }
    }


    private void processMessages() throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder currentMessage = new StringBuilder();

        int bytesRead;
        while (running && (bytesRead = inputStream.read(buffer)) != -1) {
            String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

            currentMessage.append(received);

            // Обработка сообщений, разделенных переводом строки
            while (currentMessage.indexOf("\n") != -1) {
                int endIndex = currentMessage.indexOf("\n");
                String json = currentMessage.substring(0, endIndex).trim();
                currentMessage.delete(0, endIndex + 1);

                if (!json.isEmpty()) {
                    try {
                        Message message = Message.fromJson(json);
                        handleIncomingMessage(message);
                    } catch (Exception e) {
                        System.err.println("[SERVER][ERROR] Ошибка парсинга JSON: " + e.getMessage());
                        System.err.println("[SERVER][DEBUG] Некорректный JSON: " + json);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleConnect(Message message) {
        System.out.println("[SERVER][DEBUG] Обработка CONNECT сообщения");
        if (message.getPlayerName() == null || message.getPlayerName().trim().isEmpty()) {
            System.err.println("[SERVER][ERROR] Имя игрока не может быть пустым");
            return;
        }

        // Проверка: если игра уже началась, отправляем отказ
        if (gameRoom.isGameStarted()) {
            System.out.println("[SERVER][DEBUG] Отказ в подключении: игра уже началась");
            Message rejection = new Message(MessageTypes.JOIN_REJECTED);
            rejection.setReason("Невозможно присоединиться: игра уже началась");
            sendRawMessage(rejection.toJson());
            return;
        }

        playerName = message.getPlayerName().trim();
        playerId = UUID.randomUUID().toString();
        System.out.println("[SERVER][DEBUG] Новый игрок: " + playerName + " (ID: " + playerId + ")");

        Player player = new Player(playerId, playerName);
        gameRoom.addPlayer(player);

        // Отправка подтверждения подключения
        Message response = new Message(MessageTypes.CONNECT);
        response.setPlayerId(playerId);
        response.setPlayerName(playerName);

        String jsonResponse = response.toJson();
        sendRawMessage(jsonResponse);
    }

    private void handleMove(Message message) {
        if (playerId != null) {
            gameRoom.handlePlayerMove(playerId, message.getX(), message.getY());
        }
    }

    public void sendMessage(Message message) {
        try {
            if (!socket.isClosed() && outputStream != null) {
                String messageWithNewline = message.toJson() + "\n";
                byte[] bytes = messageWithNewline.getBytes(StandardCharsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();
            }
        } catch (Exception e) {
            System.err.println("[SERVER][ERROR] Ошибка отправки: " + e.getMessage());
            disconnect();
        }
    }

    private void sendRawMessage(String message) {
        try {
            if (!socket.isClosed() && outputStream != null) {
                String messageWithNewline = message + "\n";
                byte[] bytes = messageWithNewline.getBytes(StandardCharsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();
                System.out.println("[SERVER][DEBUG] Отправлено байт: " + bytes.length);
            }
        } catch (Exception e) {
            System.err.println("[SERVER][ERROR] Ошибка отправки: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        running = false;
        try {
            if (playerId != null) {
                System.out.println("[SERVER][DEBUG] Удаление игрока из комнаты: " + playerId);
                gameRoom.removePlayer(playerId);
            }
            if (socket != null && !socket.isClosed()) {
                System.out.println("[SERVER][DEBUG] Закрытие сокета");
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при отключении клиента: " + e.getMessage());
        }
    }
    public String getPlayerId() {
        return playerId;
    }
}