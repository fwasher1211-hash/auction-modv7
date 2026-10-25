package com.example.auction.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuctionModClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of("auctionmod", "main"));

    /** DonutSMP payment notice, e.g. "Steve paid you $2.5m". Anchored so chat lines can't fake it. */
    private static final Pattern PAY = Pattern.compile(
        "^\\s*(\\w{1,16}) paid you \\$([\\d,]*\\.?\\d+)\\s*([kKmMbBtT]?)\\s*[.!]?\\s*$");

    private static KeyBinding openKey;
    static boolean debug;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.auctionmod.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) client.setScreen(new AuctionScreen());
            }
            AuctionState.I.tick(client);
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.of("auctionmod", "hud"), AuctionHud::render);

        // Payment notices arrive as system ("game") messages. Ignore the action-bar copy.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            handle(message.getString());
        });
    }

    private static void handle(String raw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (debug && mc.player != null && raw.toLowerCase().contains("paid")) {
            mc.player.sendMessage(Text.literal("[Auction debug] " + raw), false);
        }
        Matcher m = PAY.matcher(raw);
        if (!m.matches()) return;
        long amt = Money.parse(m.group(2) + m.group(3));
        if (amt > 0) AuctionState.I.onPayment(mc, m.group(1), amt);
    }
}
