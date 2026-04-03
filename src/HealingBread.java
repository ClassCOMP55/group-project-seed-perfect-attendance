/**
 * Simple consumable that restores one full heart when used from the pause inventory.
 */
public class HealingBread extends Item {

    public static final String ITEM_ID = "healing_bread";
    private static final int HEAL_AMOUNT = Player.HALF_HEARTS_PER_HEART;

    public HealingBread() {
        super(ITEM_ID, "Healing Bread", true);
    }

    @Override
    public void onUse(Player p) {
        if (p == null) return;
        int hpBefore = p.getHP();
        if (hpBefore >= p.getMaxHealth()) return;

        p.setHP(hpBefore + HEAL_AMOUNT);
        if (p.getHP() > hpBefore) {
            p.consumeInventoryItem(this);
        }
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public String getDescription() {
        return "A warm loaf that restores 1 heart. Press Space or Enter in the inventory to eat it.";
    }
}
