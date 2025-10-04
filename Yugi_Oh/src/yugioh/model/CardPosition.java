package yugioh.model;

public enum CardPosition {
    ATTACK,
    DEFENSE;

    public static CardPosition fromIndex(int index) {
        return index == 0 ? ATTACK : DEFENSE;
    }
}
