package memoryrush.server;

import memoryrush.game.GameState;
import memoryrush.game.Player;
import memoryrush.game.Card;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Server-Klasse für Memory Rush. Verwaltet Client-Verbindungen, Spielzustand und Spielablauf.
 */
public class MemoryRushServer {
    private static final int PORT = 8090;
    private static final int MAX_PLAYERS = 4;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new ArrayList<>();
    private final GameState gameState = new GameState();
    private boolean gameStarted = false;
    private Timer turnTimer = new Timer(true);
    private TimerTask currentTurnTask;

    public static void main(String[] args) {
        MemoryRushServer server = new MemoryRushServer();
        server.start();
    }

    /**
     * Startet den Server-Socket und wartet auf eingehende Client-Verbindungen.
     * Sobald genügend Spieler verbunden sind, wird das Spiel gestartet.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server gestartet auf Port " + PORT + ". Warte auf Spieler...");
            int playerCount = 0;
            while (true) {
                Socket clientSocket = serverSocket.accept();
                synchronized (this) {
                    if (gameStarted) {
                        // Keine neuen Spieler zulassen, wenn das Spiel bereits läuft
                        PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true);
                        tempOut.println("ERROR Game already in progress. Connection closed.");
                        clientSocket.close();
                        continue;
                    }
                    if (gameState.getPlayers().size() >= MAX_PLAYERS) {
                        // Lobby ist voll
                        PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true);
                        tempOut.println("ERROR Game lobby full. Connection closed.");
                        clientSocket.close();
                        continue;
                    }
                    // Neuen Spieler akzeptieren
                    playerCount++;
                    String playerName = "Player " + playerCount;
                    // Spieler zur Spielerliste hinzufügen
                    Player player = new Player(playerName);
                    gameState.getPlayers().add(player);
                    // Handler-Thread für den Client erzeugen und starten
                    ClientHandler handler = new ClientHandler(this, clientSocket, playerName);
                    clients.add(handler);
                    handler.start();
                    // Aktualisierte Spielerliste an alle Clients senden
                    broadcast("PLAYERS " + gameState.getPlayerNames());
                    System.out.println(playerName + " verbunden.");
                    // Wenn mindestens 2 Spieler verbunden sind, Spielstart planen (oder bei 4 sofort starten)
                    if (gameState.getPlayers().size() == 2) {
                        // Starte Spiel nach 5 Sekunden (Wartezeit für evtl. weitere Spieler)
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ignored) {}
                            synchronized (MemoryRushServer.this) {
                                if (!gameStarted && gameState.getPlayers().size() >= 2) {
                                    startGame();
                                }
                            }
                        }).start();
                    }
                    if (gameState.getPlayers().size() == MAX_PLAYERS) {
                        // Bei Erreichen der Maximalspielerzahl sofort starten
                        if (!gameStarted) {
                            startGame();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Startet das Memory-Spiel: mischt die Karten, benachrichtigt die Spieler und beginnt mit dem ersten Zug.
     */
    private synchronized void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        System.out.println("Spiel startet mit Spielern: " + gameState.getPlayerNames());
        // Kartendeck initialisieren und mischen (z.B. 16 Paare = 32 Karten)
        gameState.initCards(16);  // 16 Paare => 32 Karten
        // Wähle Startspieler (z.B. den ersten Spieler in der Liste oder zufällig)
        int startingIndex = 0;
        // Optional: Startspieler zufällig bestimmen
        // startingIndex = new java.util.Random().nextInt(gameState.getPlayers().size());
        // Signalisiere Spielstart und erste Runde
        broadcast("START " + gameState.getCards().size());
        // Ersten Zug bekanntgeben
        setTurn(startingIndex);
    }

    /**
     * Legt fest, welcher Spieler am Zug ist, und benachrichtigt alle Clients.
     * Startet außerdem den 30-Sekunden-Timer für den Zug dieses Spielers.
     */
    private synchronized void setTurn(int playerIndex) {
        gameState.setCurrentPlayerIndex(playerIndex);
        String playerName = gameState.getPlayers().get(playerIndex).getName();
        broadcast("TURN " + playerName);
        // Bestehenden Zug-Timer abbrechen
        if (currentTurnTask != null) {
            currentTurnTask.cancel();
        }
        // Neuen Timer für diesen Zug planen (30 Sekunden)
        currentTurnTask = new TimerTask() {
            @Override
            public void run() {
                synchronized (MemoryRushServer.this) {
                    // Prüfen, ob der Spieler noch am Zug ist und kein Zug abgeschlossen wurde
                    if (gameState.getCurrentPlayerIndex() == playerIndex && !gameState.isTurnCompleted()) {
                        // Falls ein Karte offen war und die Zeit abläuft, diese Karte zurückdecken
                        if (gameState.getFirstSelectedIndex() >= 0) {
                            int idx = gameState.getFirstSelectedIndex();
                            gameState.setFirstSelectedIndex(-1);
                            broadcast("TIMEOUT " + playerName + " " + idx);
                        } else {
                            broadcast("TIMEOUT " + playerName);
                        }
                        // Zum nächsten Spieler wechseln
                        int nextIndex = (playerIndex + 1) % gameState.getPlayers().size();
                        setTurn(nextIndex);
                    }
                }
            }
        };
        turnTimer.schedule(currentTurnTask, 30000); // 30.000 ms = 30 Sekunden
        // Markiert, dass ein neuer Zug begonnen hat (noch kein Paar versucht)
        gameState.setTurnCompleted(false);
    }

    /**
     * Sendet eine Nachricht an alle verbundenen Clients.
     */
    public synchronized void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.send(message);
        }
    }

    /**
     * Verarbeitet einen Flip-Befehl (Kartenaufdeck-Aktion) von einem Client/Spieler.
     * @param playerName Name des Spielers, der die Karte aufdeckt
     * @param index Index der Karte, die aufgedeckt werden soll
     */
    public synchronized void handleFlip(String playerName, int index) {
        if (!gameStarted) return;
        // Nur ausführen, falls dieser Spieler gerade am Zug ist
        Player currentPlayer = gameState.getPlayers().get(gameState.getCurrentPlayerIndex());
        if (!currentPlayer.getName().equals(playerName)) {
            // Nicht sein Zug -> ignorieren
            return;
        }
        // Ungültige Indizes oder bereits gefundene Karten ignorieren
        if (index < 0 || index >= gameState.getCards().size()) return;
        Card card = gameState.getCards().get(index);
        if (card.isMatched()) {
            return;
        }
        // Flip-Verarbeitung
        if (gameState.getFirstSelectedIndex() == -1) {
            // Erste Karte eines Paares wird aufgedeckt
            gameState.setFirstSelectedIndex(index);
            broadcast("FLIP " + index + " " + card.getId());
            // Noch nicht turnCompleted markieren – wartet auf zweite Karte
        } else {
            // Zweite Karte aufdecken
            int firstIndex = gameState.getFirstSelectedIndex();
            if (firstIndex == index) {
                // Derselbe Kartenindex doppelt geklickt -> ignorieren
                return;
            }
            Card firstCard = gameState.getCards().get(firstIndex);
            gameState.setFirstSelectedIndex(-1);
            broadcast("FLIP " + index + " " + card.getId());
            // Überprüfen, ob das aufgedeckte Paar übereinstimmt
            if (firstCard.getId() == card.getId()) {
                // Paar gefunden
                firstCard.setMatched(true);
                card.setMatched(true);
                currentPlayer.incrementScore();
                broadcast("MATCH " + currentPlayer.getName() + " " + firstIndex + " " + index + " " + currentPlayer.getScore());
                // Prüfen, ob alle Paare gefunden wurden (Spielende)
                if (gameState.allCardsMatched()) {
                    // Gewinner ermitteln (höchste Punktzahl, ggf. mehrere bei Gleichstand)
                    List<Player> winners = gameState.getWinners();
                    if (winners.size() == 1) {
                        broadcast("GAMEOVER " + winners.get(0).getName());
                    } else {
                        // Unentschieden, mehrere Gewinner
                        String winnerNames = "";
                        for (Player p : winners) {
                            if (!winnerNames.isEmpty()) winnerNames += ",";
                            winnerNames += p.getName();
                        }
                        broadcast("GAMEOVER TIE " + winnerNames);
                    }
                    // (Optional: Server könnte hier neu starten oder stoppen)
                } else {
                    // Spiel geht weiter: gleicher Spieler ist erneut am Zug (weiterer Versuch, da richtiges Paar)
                    setTurn(gameState.getCurrentPlayerIndex());
                }
            } else {
                // Falsch geraten (kein Match)
                broadcast("NOMATCH " + currentPlayer.getName() + " " + firstIndex + " " + index);
                // Nächster Spieler ist an der Reihe
                int nextIndex = (gameState.getCurrentPlayerIndex() + 1) % gameState.getPlayers().size();
                setTurn(nextIndex);
            }
            // Markieren, dass der Zug (Paarversuch) abgeschlossen ist – für den Timer
            gameState.setTurnCompleted(true);
        }
    }

    /**
     * Entfernt einen Client (z.B. bei Verbindungsverlust) aus der Client-Liste.
     * (Erweiterbar: z.B. andere Spieler benachrichtigen, wenn jemand das Spiel verlässt.)
     */
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        // TODO: Bei Bedarf andere Spieler informieren oder Spiel beenden, falls ein Spieler geht
    }

    /**
     * Innere Klasse zur Abwicklung der Kommunikation mit einem einzelnen Client.
     * Jede ClientHandler-Instanz läuft in einem eigenen Thread.
     */
    private class ClientHandler extends Thread {
        private MemoryRushServer server;
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName;

        public ClientHandler(MemoryRushServer server, Socket socket, String playerName) {
            this.server = server;
            this.socket = socket;
            this.playerName = playerName;
            try {
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Sende dem Client seinen zugewiesenen Spielernamen
            send("NAME " + playerName);
        }

        public void send(String msg) {
            out.println(msg);
        }

        @Override
        public void run() {
            try {
                String line;
                // Solange Eingaben vom Client empfangen, diese verarbeiten
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("FLIP:")) {
                        // Spieler möchte eine Karte aufdecken
                        try {
                            int index = Integer.parseInt(line.substring(5).trim());
                            server.handleFlip(playerName, index);
                        } catch (NumberFormatException e) {
                            // ungültiger Index – ignorieren
                        }
                    } else if (line.equals("QUIT")) {
                        // Spieler trennt die Verbindung freiwillig
                        break;
                    } else if (line.startsWith("CHAT:")) {
                        // Chat-Nachricht vom Spieler
                        String chatMsg = line.substring(5);
                        server.broadcast("CHAT " + playerName + ": " + chatMsg);
                    } else {
                        // Unbekanntes Kommando – als Chat auffassen
                        server.broadcast("CHAT " + playerName + ": " + line);
                    }
                }
            } catch (IOException e) {
                System.out.println("Verbindung zu " + playerName + " unterbrochen.");
            } finally {
                // Cleanup, wenn Client disconnectet
                server.removeClient(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}