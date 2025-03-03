package memoryrush.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Enthält den vollständigen Spielzustand: Karten, Spieler und Turn-Status. */
public class GameState {
    private List<Player> players = new ArrayList<>();
    private List<Card> cards = new ArrayList<>();
    private int currentPlayerIndex = 0;
    private int firstSelectedIndex = -1;
    private boolean turnCompleted = false;

    /**
     * Initialisiert das Kartendeck mit der angegebenen Anzahl von Kartenpaaren.
     * Es werden jeweils zwei Karten mit gleicher ID erzeugt und das Deck danach gemischt.
     * @param numPairs Anzahl der Paare (insgesamt 2*numPairs Karten)
     */
    public void initCards(int numPairs) {
        cards.clear();
        for (int id = 0; id < numPairs; id++) {
            cards.add(new Card(id));
            cards.add(new Card(id));
        }
        Collections.shuffle(cards, new Random());
    }

    public List<Player> getPlayers() {
        return players;
    }
    public List<Card> getCards() {
        return cards;
    }
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }
    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }
    public int getFirstSelectedIndex() {
        return firstSelectedIndex;
    }
    public void setFirstSelectedIndex(int firstSelectedIndex) {
        this.firstSelectedIndex = firstSelectedIndex;
    }
    public boolean isTurnCompleted() {
        return turnCompleted;
    }
    public void setTurnCompleted(boolean turnCompleted) {
        this.turnCompleted = turnCompleted;
    }

    /** Liefert die Spielernamen kommasepariert (z.B. "Player 1,Player 2"). */
    public String getPlayerNames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).getName());
            if (i < players.size() - 1) sb.append(",");
        }
        return sb.toString();
    }

    /** Prüft, ob alle Karten gefunden (gematcht) sind. */
    public boolean allCardsMatched() {
        for (Card c : cards) {
            if (!c.isMatched()) return false;
        }
        return true;
    }

    /** Bestimmt den/die Gewinner (Spieler mit der höchsten Punktzahl). */
    public List<Player> getWinners() {
        List<Player> winners = new ArrayList<>();
        int highest = -1;
        for (Player p : players) {
            if (p.getScore() > highest) {
                highest = p.getScore();
                winners.clear();
                winners.add(p);
            } else if (p.getScore() == highest) {
                winners.add(p);
            }
        }
        return winners;
    }
}
