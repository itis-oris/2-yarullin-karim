package client;

import common.Message;
import common.MessageTypes;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class NetworkService {
    private Socket socket;
    private Thread receiveThread;
    private boolean connected = false;
    private final Consumer<Message> messageHandler;
    private OutputStream outputStream;
    private InputStream inputStream;

    private String playerId;

    public NetworkService(Consumer<Message> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void sendMove(double x, double y) {
        if (!connected) return;

        try {
            Message msg = new Message(MessageTypes.MOVE);
            msg.setX(x);
            msg.setY(y);

            String json = msg.toJson();
            sendRawMessage(json);
        } catch (Exception e) {
            handleConnectionError(e);
        }
    }

    public boolean connect(String host, int port) {
        System.out.println("[CLIENT]1 Attempting to connect to " + host + ":" + port);
        try {
            socket = new Socket(host, port);
            System.out.println("[CLIENT]2 Successfully connected to server");

            // Use raw streams without wrappers
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            connected = true;

            System.out.println("[CLIENT]3 I/O streams created");

            receiveThread = new Thread(this::receiveMessages);
            receiveThread.setDaemon(true);
            receiveThread.start();

            System.out.println("[CLIENT]4 Message receiving thread started");
            return true;
        } catch (IOException e) {
            System.out.println("[CLIENT]5 CONNECTION ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (!connected) return;

        try {
            if (outputStream != null) {
                Message msg = new Message(MessageTypes.DISCONNECT);
                msg.setPlayerId(playerId);

                sendRawMessage(msg.toJson());
            }

            connected = false;
            if (receiveThread != null) {
                receiveThread.interrupt();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
    }

    public void sendConnect(String playerName) {
        if (!connected) return;
        try {
            Message msg = new Message(MessageTypes.CONNECT);
            msg.setPlayerName(playerName);

            String json = msg.toJson();
            sendRawMessage(json);

        } catch (Exception e) {
            handleConnectionError(e);
        }
    }

    private void sendRawMessage(String message) throws IOException {
        // Add newline delimiter for proper reading on the server side
        String messageWithNewline = message + "\n";
        byte[] bytes = messageWithNewline.getBytes(StandardCharsets.UTF_8);
        outputStream.write(bytes);
        outputStream.flush();
    }

    private void receiveMessages() {
        byte[] buffer = new byte[4096];
        StringBuilder currentMessage = new StringBuilder();

        try {
            int bytesRead;
            while (connected && (bytesRead = inputStream.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                currentMessage.append(received);

                // Process messages separated by newline
                while (currentMessage.indexOf("\n") != -1) {
                    int endIndex = currentMessage.indexOf("\n");
                    String json = currentMessage.substring(0, endIndex).trim();
                    currentMessage.delete(0, endIndex + 1);

                    if (!json.isEmpty()) {

                        try {
                            Message message = Message.fromJson(json);
                            Platform.runLater(() -> messageHandler.accept(message));
                        } catch (Exception e) {
                            System.err.println("[CLIENT][ERROR] Parsing error: " + e.getMessage());
                            System.err.println("[CLIENT][DEBUG] Invalid JSON: " + json);
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[CLIENT][ERROR] IOException: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                handleConnectionError(e);
            }
        } finally {
            System.out.println("[CLIENT][DEBUG] Message receiving thread finished");
            connected = false;
        }
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            showAlert("Connection lost",
                    "Connection to server was lost: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            connected = false;
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
}