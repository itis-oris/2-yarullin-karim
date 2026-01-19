package client.controllers;

import client.MainApp;
import common.Message;
import common.ScoreboardEntry;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class GameOverController {

    @FXML
    private Label titleLabel;

    @FXML
    private Label winnerLabel;

    @FXML
    private TableView<ScoreboardEntry> scoresTable;

    @FXML
    private TableColumn<ScoreboardEntry, String> nameColumn;

    @FXML
    private TableColumn<ScoreboardEntry, Integer> winsColumn;

    @FXML
    private javafx.scene.control.Button returnButton;

    private MainApp mainApp;
    private Message gameResultMessage;

    @FXML
    private void initialize() {
        // Настройка таблицы
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("playerName"));
        winsColumn.setCellValueFactory(new PropertyValueFactory<>("wins"));

        // Включаем политику автоматического изменения размера колонок
        scoresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setMessageData(Message message) {
        this.gameResultMessage = message;
        // Обновляем интерфейс с данными
        if (message != null) {
            updateUIWithMessage(message);
        }
    }

    private void updateUIWithMessage(Message message) {
        // Установка победителя
        String winner = message.getWinner();
        if (winner != null && !winner.isEmpty() && !"Вы проиграли!".equals(message.getWinner())) {
            winnerLabel.setText("Победитель: " + winner);
            winnerLabel.setStyle("-fx-text-fill: #27ae60;");
        } else if ("Вы проиграли!".equals(message.getWinner())) {
            winnerLabel.setText("Вы проиграли!");
            winnerLabel.setStyle("-fx-text-fill: #e74c3c;");
        } else {
            winnerLabel.setText("Ничья!");
            winnerLabel.setStyle("-fx-text-fill: #f39c12;");
        }


        // Заполнение таблицы результатов
        if (message.getScores() != null && !message.getScores().isEmpty()) {
            scoresTable.setItems(FXCollections.observableArrayList(message.getScores()));
        }
    }

    @FXML
    private void returnToMain() {
        if (mainApp != null) {
            mainApp.showConnectionScreen();
        }
    }
}