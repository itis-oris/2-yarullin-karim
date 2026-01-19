package client.screens;

import client.MainApp;
import client.NetworkService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class ConnectionScreen extends VBox {
    private final MainApp app;
    private final NetworkService networkService;
    private final TextField ipField;
    private final TextField portField;
    private final TextField nameField;
    private final Button connectButton;
    private final Label statusLabel;
    private final CheckBox showCompassCheckBox;

    private static boolean showCompass = true;
    private static String name;
    private static String ip = "localhost";
    private static String portText = "5556";


    public ConnectionScreen(MainApp app, NetworkService networkService) {
        this.app = app;
        this.networkService = networkService;

        setPadding(new Insets(20));
        setSpacing(10);
        setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Подключение к серверу ColorRush");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        ipField = new TextField(ip);
        ipField.setPromptText("IP адрес сервера");

        portField = new TextField(portText);
        portField.setPromptText("Порт сервера");

        nameField = new TextField(name);
        nameField.setPromptText("Ваше имя");

        connectButton = new Button("Подключиться");
        connectButton.setOnAction(e -> connectToServer());

        showCompassCheckBox = new CheckBox("Показать компасс");
        showCompassCheckBox.setSelected(showCompass);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: red;");

        getChildren().addAll(titleLabel, ipField, portField, nameField, connectButton, statusLabel, showCompassCheckBox);
    }

    private void connectToServer() {
        ip = ipField.getText().trim();
        portText = portField.getText().trim();
        name = nameField.getText().trim();
        showCompass = showCompassCheckBox.isSelected();

        if (name.isEmpty()) {
            statusLabel.setText("Введите имя игрока");
            return;
        }

        if (name.length() > 15) {
            statusLabel.setText("Имя слишком длинное (макс. 15 символов)");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1024 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Неверный номер порта");
            return;
        }

        statusLabel.setText("Подключение...");

        // Подключение в отдельном потоке
        new Thread(() -> {
            boolean connected = networkService.connect(ip, port);
            if (connected) {
                networkService.sendConnect(name);

            } else {
                javafx.application.Platform.runLater(() -> {
                    connectButton.setDisable(false);
                    statusLabel.setText("Не удалось подключиться");
                });
            }
        }).start();
        connectButton.setDisable(false);
        statusLabel.setText("Повторим?");
    }

    public boolean getShowCompass() {
        return showCompass;
    }
}