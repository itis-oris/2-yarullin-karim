package client.controllers;

import client.MainApp;
import client.NetworkService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ConnectionController {
    @FXML
    private TextField ipField;

    @FXML
    private TextField portField;

    @FXML
    private TextField nameField;

    @FXML
    private Button connectButton;

    @FXML
    private Label statusLabel;

    @FXML
    private CheckBox showCompassCheckBox;

    private MainApp mainApp;
    private NetworkService networkService;

    private static boolean showCompass = true;
    private static String name;
    private static String ip = "localhost";
    private static String portText = "5556";

    @FXML
    private void initialize() {
        // Загрузка сохраненных значений
        ipField.setText(ip);
        portField.setText(portText);
        nameField.setText(name != null ? name : "");
        showCompassCheckBox.setSelected(showCompass);
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setNetworkService(NetworkService networkService) {
        this.networkService = networkService;
    }

    @FXML
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
        connectButton.setDisable(true);

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
    }

    public boolean getShowCompass() {
        return showCompass;
    }
}