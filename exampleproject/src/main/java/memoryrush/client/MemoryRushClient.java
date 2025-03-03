package memoryrush.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Client-Anwendung für Memory Rush. Stellt die JavaFX-Oberfläche bereit und kommuniziert mit dem Server.
 */
public class MemoryRushClient extends Application {
    private PrintWriter out;
    private BufferedReader in;
    private String myName = "";
    private boolean myTurn = false;
    private final Map<String, Label> scoreLabels = new HashMap<>();
    private HBox scoreboardBox;
    private Label turnLabel;
    private Label timeLabel;
    private TextArea chatArea;
    private TextField chatField;
    private Button[] cardButtons;
    private boolean[] matched;
    private int openIndex = -1;
    private boolean waitingForResult = false;
    private Timeline timerTimeline;
    private int timeRemaining = 30;
    // Emojis für Kartenmotive (für Karten-IDs 0-15)
    private final String[] emojiFaces = {
            "\uD83D\uDC36", // 🐶
            "\uD83D\uDC31", // 🐱
            "\uD83D\uDC2D", // 🐭
            "\uD83D\uDC39", // 🐹
            "\uD83D\uDC30", // 🐰
            "\uD83E\uDD8A", // 🦊
            "\uD83D\uDC3B", // 🐻
            "\uD83D\uDC3C", // 🐼
            "\uD83D\uDC28", // 🐨
            "\uD83D\uDC2F", // 🐯
            "\uD83E\uDD81", // 🦁
            "\uD83D\uDC2E", // 🐮
            "\uD83D\uDC37", // 🐷
            "\uD83D\uDC35", // 🐵
            "\uD83D\uDC24", // 🐤 (Küken)
            "\uD83D\uDC38"  // 🐸
    };

    @Override
    public void start(Stage primaryStage) {
        // UI-Komponenten aufbauen
        scoreboardBox = new HBox(20);
        scoreboardBox.setAlignment(Pos.CENTER);
        scoreboardBox.setPadding(new Insets(10));
        turnLabel = new Label("Aktueller Zug: -");
        timeLabel = new Label("Zeit: 30");
        timeLabel.setPrefWidth(100);
        turnLabel.setPrefWidth(200);
        // Untere Leiste mit aktuellem Zug und Timer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomBar = new HBox(10, turnLabel, spacer, timeLabel);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER);
        // Chat-Bereich (rechts)
        chatArea = new TextArea();
        chatArea.setPrefWidth(250);
        chatArea.setPrefHeight(400);
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        Label chatLabel = new Label("Chat");
        chatLabel.setStyle("-fx-font-weight: bold;");
        chatField = new TextField();
        chatField.setPrefWidth(180);
        Button sendButton = new Button("Senden");
        sendButton.setPrefWidth(70);
        // Sende-Action für Chat (Button oder Enter im Textfeld)
        sendButton.setOnAction(e -> sendChat());
        chatField.setOnAction(e -> sendChat());
        HBox chatInputBar = new HBox(5, chatField, sendButton);
        chatInputBar.setAlignment(Pos.CENTER_LEFT);
        VBox chatBox = new VBox(5, chatLabel, chatArea, chatInputBar);
        chatBox.setPadding(new Insets(10));
        chatBox.setPrefWidth(270);
        // Spielfeld-Gitter (anfangs leer, wird nach "START" aufgebaut)
        GridPane cardGrid = new GridPane();
        cardGrid.setHgap(10);
        cardGrid.setVgap(10);
        cardGrid.setPadding(new Insets(10));
        // Haupt-Layout zusammenbauen
        BorderPane root = new BorderPane();
        root.setTop(scoreboardBox);
        root.setCenter(cardGrid);
        root.setRight(chatBox);
        root.setBottom(bottomBar);
        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setTitle("Memory Rush");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Verbindung zum Server herstellen
        connectToServer();

        // Thread starten, der auf Server-Nachrichten lauscht
        Thread listener = new Thread(this::listenToServer);
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Stellt die Verbindung zum Server her.
     */
    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 8090);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            showError("Verbindung zum Server fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Lauscht auf Nachrichten vom Server (in eigenem Thread) und reicht sie zur Verarbeitung weiter.
     */
    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                // Im JavaFX Application Thread verarbeiten (UI-Updates)
                Platform.runLater(() -> processServerMessage(msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> showError("Serververbindung verloren: " + e.getMessage()));
        }
    }

    /**
     * Verarbeitet eine einzelne Nachricht vom Server und aktualisiert entsprechend die UI oder internen Zustand.
     */
    private void processServerMessage(String message) {
        if (message.startsWith("NAME ")) {
            // Zuweisung des eigenen Spielernamens vom Server
            myName = message.substring(5);
            // (Optional könnte man den Namen z.B. im Fenstertitel anzeigen)
        } else if (message.startsWith("PLAYERS ")) {
            // Komplette Spielerliste (Komma-separierte Namen)
            String namesList = message.substring(8);
            String[] names = namesList.split(",");
            scoreboardBox.getChildren().clear();
            scoreLabels.clear();
            for (String name : names) {
                name = name.trim();
                Label lbl = new Label(name + (name.equals(myName) ? " (You)" : "") + ": 0");
                if (name.equals(myName)) {
                    lbl.setStyle("-fx-text-fill: blue;");
                }
                scoreboardBox.getChildren().add(lbl);
                scoreLabels.put(name, lbl);
            }
        } else if (message.startsWith("START")) {
            // Spielbeginn – Aufbau des Kartenfeldes
            String[] parts = message.split(" ");
            int totalCards = 16;
            if (parts.length > 1) {
                try {
                    totalCards = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    // Falls keine Zahl mitgesendet, Standardwert 16 (4x4)
                }
            }
            initCardGrid(totalCards);
            chatArea.appendText("Das Spiel hat begonnen!\n");
        } else if (message.startsWith("TURN ")) {
            String playerName = message.substring(5);
            turnLabel.setText("Aktueller Zug: " + playerName);
            // Hervorheben, wer am Zug ist (Scoreboard)
            for (Map.Entry<String, Label> entry : scoreLabels.entrySet()) {
                String name = entry.getKey();
                Label lbl = entry.getValue();
                if (name.equals(playerName)) {
                    lbl.setStyle(lbl.getStyle() + "-fx-font-weight: bold; -fx-underline: true;");
                } else {
                    // Hervorhebung entfernen (bei eigenem Namen Blau beibehalten)
                    if (name.equals(myName)) {
                        lbl.setStyle("-fx-text-fill: blue;");
                    } else {
                        lbl.setStyle("");
                    }
                }
            }
            // Merken, ob der lokale Spieler am Zug ist
            myTurn = playerName.equals(myName);
            // Reset des Auswahl-Status für neuen Zug
            openIndex = -1;
            waitingForResult = false;
            // Karten-Buttons (de)aktivieren je nachdem, ob eigener Zug
            updateCardButtonsState();
            // Runden-Timer (Countdown) neu starten
            if (timerTimeline != null) {
                timerTimeline.stop();
            }
            timeRemaining = 30;
            timeLabel.setText("Zeit: 30");
            timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                timeRemaining--;
                timeLabel.setText("Zeit: " + timeRemaining);
                if (timeRemaining <= 0) {
                    timerTimeline.stop();
                }
            }));
            timerTimeline.setCycleCount(30);
            timerTimeline.play();
        } else if (message.startsWith("FLIP ")) {
            // Eine Karte wird aufgedeckt (Server teilt Index und Motiv-ID mit)
            StringTokenizer st = new StringTokenizer(message);
            st.nextToken(); // "FLIP"
            int idx = Integer.parseInt(st.nextToken());
            int cardId = Integer.parseInt(st.nextToken());
            if (idx >= 0 && idx < cardButtons.length) {
                Button cardBtn = cardButtons[idx];
                // Emoji oder ID ermitteln
                String face = (cardId >= 0 && cardId < emojiFaces.length)
                        ? emojiFaces[cardId]
                        : String.valueOf(cardId);
                // Flip-Animation: Karte erst zuklappen, dann Motiv zeigen
                ScaleTransition st1 = new ScaleTransition(Duration.millis(150), cardBtn);
                st1.setFromX(1.0);
                st1.setToX(0.0);
                ScaleTransition st2 = new ScaleTransition(Duration.millis(150), cardBtn);
                st2.setFromX(0.0);
                st2.setToX(1.0);
                st1.setOnFinished(e -> {
                    cardBtn.setText(face);
                });
                SequentialTransition flipAnim = new SequentialTransition(st1, st2);
                flipAnim.play();
                // Karte während sie offen ist deaktivieren
                cardBtn.setDisable(true);
                if (openIndex == -1) {
                    // Erste Karte eines Paares wurde umgedreht
                    openIndex = idx;
                } else {
                    // Zweite Karte umgedreht, Ergebnis folgt in MATCH/NOMATCH
                    // (Keine weitere Aktion hier nötig)
                }
            }
        } else if (message.startsWith("MATCH ")) {
            // Ein Paar wurde gefunden: MATCH Spieler idx1 idx2 neuerScore
            String[] parts = message.split(" ");
            if (parts.length >= 5) {
                String playerName = parts[1];
                int idx1 = Integer.parseInt(parts[2]);
                int idx2 = Integer.parseInt(parts[3]);
                int newScore = Integer.parseInt(parts[4]);
                // Gefundene Karten bleiben offen (leicht ausgegraut zur Markierung)
                if (idx1 >= 0 && idx1 < cardButtons.length) {
                    cardButtons[idx1].setDisable(true);
                    cardButtons[idx1].setStyle("-fx-opacity: 0.7;");
                }
                if (idx2 >= 0 && idx2 < cardButtons.length) {
                    cardButtons[idx2].setDisable(true);
                    cardButtons[idx2].setStyle("-fx-opacity: 0.7;");
                }
                matched[idx1] = true;
                matched[idx2] = true;
                // Punktestand im Scoreboard aktualisieren
                if (scoreLabels.containsKey(playerName)) {
                    Label lbl = scoreLabels.get(playerName);
                    lbl.setText(playerName + (playerName.equals(myName) ? " (You)" : "") + ": " + newScore);
                }
                // Auswahl zurücksetzen, Zug geht ggf. für selben Spieler weiter
                openIndex = -1;
                waitingForResult = false;
            }
        } else if (message.startsWith("NOMATCH ")) {
            // Kein Paar: NOMATCH Spieler idx1 idx2
            String[] parts = message.split(" ");
            if (parts.length >= 4) {
                int idx1 = Integer.parseInt(parts[2]);
                int idx2 = Integer.parseInt(parts[3]);
                // Kleiner Delay, dann Karten zurückdrehen
                PauseTransition pause = new PauseTransition(Duration.seconds(1));
                pause.setOnFinished(ev -> {
                    if (idx1 >= 0 && idx1 < cardButtons.length) {
                        cardButtons[idx1].setText("❓");
                        if (!matched[idx1]) {
                            cardButtons[idx1].setDisable(false);
                        }
                    }
                    if (idx2 >= 0 && idx2 < cardButtons.length) {
                        cardButtons[idx2].setText("❓");
                        if (!matched[idx2]) {
                            cardButtons[idx2].setDisable(false);
                        }
                    }
                });
                pause.play();
                openIndex = -1;
                waitingForResult = false;
            }
        } else if (message.startsWith("TIMEOUT ")) {
            // Zug-Zeit abgelaufen: TIMEOUT Spieler [idxOffen]
            String[] parts = message.split(" ");
            if (parts.length >= 2) {
                String playerName = parts[1];
                String info;
                if (playerName.equals(myName)) {
                    info = "Deine Zeit ist abgelaufen!\n";
                } else {
                    info = "Die Zeit von " + playerName + " ist abgelaufen.\n";
                }
                chatArea.appendText(info);
                if (parts.length == 3) {
                    // Eine offene Karte wird zurückgedeckt
                    int idx = Integer.parseInt(parts[2]);
                    if (idx >= 0 && idx < cardButtons.length) {
                        cardButtons[idx].setText("❓");
                        if (!matched[idx]) {
                            cardButtons[idx].setDisable(false);
                        }
                    }
                }
                openIndex = -1;
                waitingForResult = false;
            }
        } else if (message.startsWith("GAMEOVER ")) {
            // Spielende: entweder "GAMEOVER Name" oder "GAMEOVER TIE name1,name2"
            if (timerTimeline != null) {
                timerTimeline.stop();
            }
            String content = message.substring(9);
            String endMsg;
            if (content.startsWith("TIE")) {
                String tieNames = content.substring(4);
                endMsg = "Spielende! Unentschieden zwischen: " + tieNames + ".\n";
            } else {
                endMsg = "Spielende! Gewinner: " + content + "\n";
            }
            chatArea.appendText(endMsg);
            // Alle Kartenzüge deaktivieren (Spiel vorbei)
            for (Button btn : cardButtons) {
                btn.setDisable(true);
            }
        } else if (message.startsWith("CHAT ")) {
            // Chat-Nachricht anzeigen
            String chatMsg = message.substring(5);
            chatArea.appendText(chatMsg + "\n");
        } else if (message.startsWith("ERROR")) {
            // Fehlernachricht vom Server (z.B. Spiel schon gestartet)
            showError(message + "\n");
        }
    }

    /**
     * Initialisiert das Kartengitter (alle Karten verdeckt anzeigen) basierend auf der Kartenzahl.
     */
    private void initCardGrid(int totalCards) {
        // Gittergröße bestimmen (möglichst rechteckig)
        int rows = (int) Math.floor(Math.sqrt(totalCards));
        while (rows > 1 && totalCards % rows != 0) {
            rows--;
        }
        int cols = totalCards / rows;
        GridPane cardGrid = new GridPane();
        cardGrid.setHgap(10);
        cardGrid.setVgap(10);
        cardGrid.setPadding(new Insets(10));
        cardButtons = new Button[totalCards];
        matched = new boolean[totalCards];
        for (int i = 0; i < totalCards; i++) {
            matched[i] = false;
        }
        for (int i = 0; i < totalCards; i++) {
            Button cardBtn = new Button("❓");
            cardBtn.setPrefSize(80, 80);
            cardBtn.setStyle("-fx-font-size: 24; -fx-background-color: #FFA500; -fx-text-fill: #000000;");
            final int idx = i;
            cardBtn.setOnAction(e -> handleCardClick(idx));
            cardButtons[i] = cardBtn;
            int r = i / cols;
            int c = i % cols;
            cardGrid.add(cardBtn, c, r);
        }
        // Neues Grid in der UI anzeigen
        BorderPane root = (BorderPane) turnLabel.getScene().getRoot();
        root.setCenter(cardGrid);
    }

    /**
     * Behandelt den Klick auf eine verdeckte Karte durch den Benutzer.
     */
    private void handleCardClick(int index) {
        if (!myTurn || waitingForResult) {
            return;
        }
        // Nur erlauben, wenn Karte nicht bereits gefunden und nicht schon offen ist
        if (matched[index] || index == openIndex) {
            return;
        }
        // Flip-Befehl an Server senden
        out.println("FLIP:" + index);
        // Wenn dies die zweite Karte im Zug war, auf Ergebnis warten (keine weiteren Klicks zulassen)
        if (openIndex != -1) {
            waitingForResult = true;
        }
    }

    /**
     * Aktualisiert den Aktivierungszustand aller Kartenbuttons je nach Spielzug.
     */
    private void updateCardButtonsState() {
        if (cardButtons == null) return;
        for (int i = 0; i < cardButtons.length; i++) {
            if (matched[i]) {
                // Bereits gefundene Paare bleiben deaktiviert
                cardButtons[i].setDisable(true);
            } else {
                // Nur im eigenen Zug dürfen verdeckte, nicht gefundene Karten angeklickt werden
                cardButtons[i].setDisable(!myTurn);
            }
        }
    }

    /**
     * Versendet den aktuellen Text im Chat-Eingabefeld an den Server.
     */
    private void sendChat() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) return;
        out.println("CHAT:" + text);
        chatField.clear();
    }

    /**
     * Zeigt eine Fehlermeldung im Chat-Bereich an (z.B. Verbindungsprobleme).
     */
    private void showError(String errorMsg) {
        chatArea.appendText("ERROR: " + errorMsg);
    }

    public static void main(String[] args) {
        launch(args);
    }
}