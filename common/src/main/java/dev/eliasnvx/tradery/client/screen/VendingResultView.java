package dev.eliasnvx.tradery.client.screen;

import net.minecraft.network.chat.Component;

/** A vending screen that shows the server's answer to a trade or a save. */
public interface VendingResultView {
    void showResult(boolean success, Component message);
}
