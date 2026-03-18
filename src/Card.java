
public class Card {

    private String id;
    private String name;
    private String description;
    private CardType type;

    public Card(String id, String name, String description, CardType type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CardType getType() {
        return type;
    }

}
