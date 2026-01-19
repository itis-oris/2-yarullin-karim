package client.screens;

import client.MainApp;
import common.Message;
import common.ScoreboardEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class GameOverScreen extends BorderPane {

    private final MainApp app;

    public GameOverScreen(MainApp app, Message message) {
        this.app = app;
        setPadding(new Insets(20));

        Label titleLabel = new Label("Игра окончена");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));

        Label winnerLabel = new Label();
        winnerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        if (message.getWinner() != null && !message.getWinner().isEmpty()) {
            winnerLabel.setText("Победитель: " + message.getWinner());
            winnerLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            winnerLabel.setText("Ничья!");
            winnerLabel.setStyle("-fx-text-fill: #f39c12;");
        }

        TableView<ScoreboardEntry> scoresTable = new TableView<>();
        scoresTable.setPrefWidth(400);
        scoresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ScoreboardEntry, String> nameCol = new TableColumn<>("Игрок");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("playerName"));

        TableColumn<ScoreboardEntry, Integer> winsCol = new TableColumn<>("Побед");
        winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));

        scoresTable.getColumns().addAll(nameCol, winsCol);

        if (message.getScores() != null && !message.getScores().isEmpty()) {
            scoresTable.getItems().setAll(message.getScores());
        }

        Button returnButton = new Button("Вернуться в главное меню");
        returnButton.setStyle("-fx-font-size: 16px; -fx-padding: 10px 20px;");
        returnButton.setOnAction(e -> app.showConnectionScreen());

        VBox centerBox = new VBox(20, titleLabel, winnerLabel, scoresTable, returnButton);
        centerBox.setAlignment(Pos.CENTER);

        setCenter(centerBox);
    }
}
