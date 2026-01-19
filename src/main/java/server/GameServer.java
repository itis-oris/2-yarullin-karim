package server;

import server.db.ScoreboardRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    private static final int PORT = 5556;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreads = Executors.newCachedThreadPool();
    private final GameRoom gameRoom;
    private final ScoreboardRepository scoreboard;

    public GameServer() {
        this.scoreboard = new ScoreboardRepository();
        this.gameRoom = new GameRoom(scoreboard);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, gameRoom);
                clientThreads.submit(handler);
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("Ошибка сервера: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            clientThreads.shutdownNow();
        } catch (IOException e) {
            System.err.println("Ошибка при остановке сервера: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer();

        // Обработка завершения по Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}