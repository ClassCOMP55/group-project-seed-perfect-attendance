import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Shop overlay used by the BreadMerchant NPC.
 */
public class ShopMenu extends GraphicsPane {
    public static final int HEALING_BREAD_PRICE = 5;

    private static final Color DIM = new Color(0, 0, 0, 170);
    private static final Color PANEL_BG = new Color(42, 30, 18);
    private static final Color PANEL_BORDER = new Color(177, 124, 55);
    private static final Color INNER_BG = new Color(62, 44, 28);
    private static final Color SELECTED = new Color(255, 215, 120);
    private static final Color NORMAL = new Color(232, 229, 214);
    private static final Color STATUS = new Color(166, 235, 176);
    private static final Color STATUS_WARN = new Color(245, 166, 166);

    private Player player;
    private Runnable onClose;
    private String merchantName = "Bread Merchant";
    private int focusIndex;

    private GRect dimOverlay;
    private GRoundRect panel;
    private GRoundRect stockBox;
    private GRect buyHit;
    private GRect leaveHit;
    private GLabel titleLabel;
    private GLabel helpLabel;
    private GLabel walletLabel;
    private GLabel ownedLabel;
    private GLabel stockLabel;
    private GLabel buyLabel;
    private GLabel leaveLabel;
    private GLabel statusLabel;

    public ShopMenu(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    public void openFor(Player player, String merchantName, Runnable onClose) {
        if (player == null) return;
        close();
        this.player = player;
        this.onClose = onClose;
        if (merchantName != null && !merchantName.trim().isEmpty()) {
            this.merchantName = merchantName.trim();
        }
        this.focusIndex = 0;
        GamePlayState.setCurrent(GamePlayState.INVENTORY);
        buildUi();
        refreshLabels();
    }

    public boolean isOpen() {
        return !contents.isEmpty();
    }

    public void close() {
        boolean wasOpen = !contents.isEmpty();
        for (GObject obj : contents) {
            mainScreen.remove(obj);
        }
        contents.clear();
        if (wasOpen) {
            GamePlayState.setCurrent(GamePlayState.PLAYING);
        }
        player = null;
        onClose = null;
        panel = null;
        stockBox = null;
        buyHit = null;
        leaveHit = null;
        statusLabel = null;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (!isOpen()) return;
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_ESCAPE) {
            closeAndResume();
            return;
        }
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) {
            focusIndex = Math.max(0, focusIndex - 1);
            refreshLabels();
            return;
        }
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) {
            focusIndex = Math.min(1, focusIndex + 1);
            refreshLabels();
            return;
        }
        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
            activateFocusedOption();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!isOpen()) return;
        double mx = e.getX();
        double my = e.getY();
        if ((buyHit != null && buyHit.contains(mx, my))
            || (buyLabel != null && buyLabel.contains(mx, my))) {
            focusIndex = 0;
            activateFocusedOption();
            return;
        }
        if ((leaveHit != null && leaveHit.contains(mx, my))
            || (leaveLabel != null && leaveLabel.contains(mx, my))) {
            focusIndex = 1;
            activateFocusedOption();
        }
    }

    private void activateFocusedOption() {
        if (focusIndex == 0) {
            tryBuyHealingBread();
            return;
        }
        closeAndResume();
    }

    private void tryBuyHealingBread() {
        if (player == null) {
            setStatus("No player is active.", true);
            return;
        }
        if (player.getCoins() < HEALING_BREAD_PRICE) {
            setStatus("Not enough coins.", true);
            return;
        }
        player.addCoins(-HEALING_BREAD_PRICE);
        player.collectItem(new HealingBread());
        GameSFX.play(GameSFX.SFX.ITEM_USE);
        setStatus("Purchased 1 Healing Bread.", false);
        refreshLabels();
    }

    private void closeAndResume() {
        Runnable callback = onClose;
        close();
        if (callback != null) callback.run();
    }

    private void setStatus(String message, boolean warn) {
        if (statusLabel == null) return;
        statusLabel.setLabel(message);
        statusLabel.setColor(warn ? STATUS_WARN : STATUS);
    }

    private void buildUi() {
        double fw = mainScreen.getWidth();
        double fh = mainScreen.getHeight();

        dimOverlay = new GRect(0, 0, fw, fh);
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(DIM);
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        place(dimOverlay);

        double panelW = 700;
        double panelH = 360;
        double px = (fw - panelW) / 2.0;
        double py = (fh - panelH) / 2.0;
        panel = new GRoundRect(px, py, panelW, panelH, 16, 16);
        panel.setFilled(true);
        panel.setFillColor(PANEL_BG);
        panel.setColor(PANEL_BORDER);
        place(panel);

        titleLabel = new GLabel("", px + 28, py + 46);
        titleLabel.setFont("Courier New-BOLD-24");
        titleLabel.setColor(SELECTED);
        place(titleLabel);

        helpLabel = new GLabel("W/S or Up/Down move   Enter/Space select   Esc close", px + 28, py + 74);
        helpLabel.setFont("Courier New-BOLD-13");
        helpLabel.setColor(NORMAL);
        place(helpLabel);

        walletLabel = new GLabel("", px + 28, py + 112);
        walletLabel.setFont("Courier New-BOLD-18");
        walletLabel.setColor(NORMAL);
        place(walletLabel);

        ownedLabel = new GLabel("", px + 28, py + 138);
        ownedLabel.setFont("Courier New-BOLD-14");
        ownedLabel.setColor(NORMAL);
        place(ownedLabel);

        stockBox = new GRoundRect(px + 24, py + 156, panelW - 48, 130, 12, 12);
        stockBox.setFilled(true);
        stockBox.setFillColor(INNER_BG);
        stockBox.setColor(PANEL_BORDER);
        place(stockBox);

        stockLabel = new GLabel("Stock", px + 44, py + 184);
        stockLabel.setFont("Courier New-BOLD-16");
        stockLabel.setColor(SELECTED);
        place(stockLabel);

        buyHit = new GRect(px + 40, py + 196, panelW - 80, 30);
        buyHit.setFilled(true);
        buyHit.setFillColor(INNER_BG);
        buyHit.setColor(INNER_BG);
        place(buyHit);

        buyLabel = new GLabel("", px + 48, py + 218);
        buyLabel.setFont("Courier New-BOLD-20");
        place(buyLabel);

        leaveHit = new GRect(px + 40, py + 234, panelW - 80, 30);
        leaveHit.setFilled(true);
        leaveHit.setFillColor(INNER_BG);
        leaveHit.setColor(INNER_BG);
        place(leaveHit);

        leaveLabel = new GLabel("", px + 48, py + 256);
        leaveLabel.setFont("Courier New-BOLD-20");
        place(leaveLabel);

        statusLabel = new GLabel("", px + 28, py + 320);
        statusLabel.setFont("Courier New-BOLD-16");
        statusLabel.setColor(STATUS);
        place(statusLabel);
    }

    private void refreshLabels() {
        if (titleLabel == null) return;
        titleLabel.setLabel(merchantName + " Shop");
        int coins = player == null ? 0 : player.getCoins();
        int ownedBread = player == null ? 0 : player.getItemCount(HealingBread.ITEM_ID);
        walletLabel.setLabel("Coins: " + coins);
        ownedLabel.setLabel("Healing Bread in inventory: " + ownedBread);
        buyLabel.setLabel((focusIndex == 0 ? "> " : "  ") + "Healing Bread  -  " + HEALING_BREAD_PRICE + " coins");
        leaveLabel.setLabel((focusIndex == 1 ? "> " : "  ") + "Leave");
        buyHit.setFillColor(focusIndex == 0 ? new Color(97, 74, 50) : INNER_BG);
        leaveHit.setFillColor(focusIndex == 1 ? new Color(97, 74, 50) : INNER_BG);
        buyLabel.setColor(focusIndex == 0 ? SELECTED : NORMAL);
        leaveLabel.setColor(focusIndex == 1 ? SELECTED : NORMAL);
    }
}
