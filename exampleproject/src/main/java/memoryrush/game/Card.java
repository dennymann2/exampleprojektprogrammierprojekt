package memoryrush.game;

/** Repräsentiert eine Spielkarte im Memory. */
public class Card {
    private int id;
    private boolean matched;

    public Card(int id) {
        this.id = id;
        this.matched = false;
    }

    public int getId() {
        return id;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}