package yugioh.model;

import java.util.Objects;

public class Card {
    private final int id;
    private final String name;
    private final String type;
    private final int atk;
    private final int def;
    private final String description;
    private final String imageUrl;

    public Card(int id, String name, String type, int atk, int def, String description, String imageUrl) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.atk = atk;
        this.def = def;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getAtk() {
        return atk;
    }

    public int getDef() {
        return def;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isMonster() {
        return type != null && type.toLowerCase().contains("monster");
    }

    @Override
    public String toString() {
        return name + " (ATK: " + atk + ", DEF: " + def + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        Card card = (Card) o;
        return id == card.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
