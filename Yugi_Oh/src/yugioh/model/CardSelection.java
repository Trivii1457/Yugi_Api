package yugioh.model;

public class CardSelection {
    private final Card card;
    private final CardPosition position;

    public CardSelection(Card card, CardPosition position) {
        this.card = card;
        this.position = position;
    }

    public Card getCard() {
        return card;
    }

    public CardPosition getPosition() {
        return position;
    }
}
