package com.example.countchat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = CountChat.MOD_ID, dist = Dist.CLIENT)
public class CountChat {
    public static final String MOD_ID = "countchat";

    private static final int DELAY_TICKS = 20;
    private static final int MAX_NUMBER = 100;

    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.countchat.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.countchat"
    );

    private boolean running = false;
    private int current = 1;
    private int cooldown = 0;

    public CountChat(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.player.connection == null) {
            running = false;
            return;
        }

        while (TOGGLE_KEY.consumeClick()) {
            running = !running;
            if (running) {
                current = 1;
                cooldown = 0;
                mc.player.displayClientMessage(Component.literal("§aСчёт запущен"), true);
            } else {
                mc.player.displayClientMessage(Component.literal("§cСчёт остановлен"), true);
            }
        }

        if (!running) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        mc.player.connection.sendChat(String.valueOf(current));
        current++;
        cooldown = DELAY_TICKS - 1;

        if (current > MAX_NUMBER) {
            running = false;
            mc.player.displayClientMessage(Component.literal("§eСчёт завершён"), true);
        }
    }
}
