package com.example.countchat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Mod(value = CountChat.MOD_ID, dist = Dist.CLIENT)
public class CountChat {
    public static final String MOD_ID = "countchat";

    static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.countchat.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.countchat"
    );

    public CountChat(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!Settings.loaded) {
            Settings.load();
        }

        if (mc.player == null || mc.player.connection == null) {
            Counter.stop();
            return;
        }

        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MenuScreen());
            }
        }

        Counter.tick(mc);
    }

    // ------------------------------------------------------------------
    // Настройки (сохраняются в config/countchat.properties)
    // ------------------------------------------------------------------
    static final class Settings {
        static final int MIN_DELAY = 1;
        static final int MAX_DELAY = 200;

        static boolean loaded = false;
        static int delayTicks = 20;   // пауза между сообщениями, в тиках
        static int from = 1;          // с какого числа
        static int to = 100;          // до какого числа
        static boolean loop = false;  // повторять по кругу

        private static Path file() {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("countchat.properties");
        }

        static void load() {
            loaded = true;
            Path file = file();
            if (!Files.exists(file)) {
                return;
            }
            try (var in = Files.newInputStream(file)) {
                Properties p = new Properties();
                p.load(in);
                delayTicks = Mth.clamp(Integer.parseInt(p.getProperty("delayTicks", "20")), MIN_DELAY, MAX_DELAY);
                from = Math.max(0, Integer.parseInt(p.getProperty("from", "1")));
                to = Math.max(0, Integer.parseInt(p.getProperty("to", "100")));
                loop = Boolean.parseBoolean(p.getProperty("loop", "false"));
            } catch (Exception ignored) {
                // повреждённый файл: остаются значения по умолчанию
            }
        }

        static void save() {
            try {
                Path file = file();
                Files.createDirectories(file.getParent());
                Properties p = new Properties();
                p.setProperty("delayTicks", String.valueOf(delayTicks));
                p.setProperty("from", String.valueOf(from));
                p.setProperty("to", String.valueOf(to));
                p.setProperty("loop", String.valueOf(loop));
                try (var out = Files.newOutputStream(file)) {
                    p.store(out, "Count Chat settings");
                }
            } catch (Exception ignored) {
                // не получилось сохранить: не страшно
            }
        }
    }

    // ------------------------------------------------------------------
    // Сам счёт
    // ------------------------------------------------------------------
    static final class Counter {
        private static boolean running = false;
        private static int next = 1;
        private static int ticksSinceLast = 0;

        static boolean isRunning() {
            return running;
        }

        static int getNext() {
            return next;
        }

        static void start() {
            next = Settings.from;
            ticksSinceLast = Settings.delayTicks; // первое сообщение уйдёт сразу
            running = true;
        }

        static void stop() {
            running = false;
        }

        static void tick(Minecraft mc) {
            if (!running) {
                return;
            }
            ticksSinceLast++;
            if (ticksSinceLast < Settings.delayTicks) {
                return;
            }
            ticksSinceLast = 0;

            mc.player.connection.sendChat(String.valueOf(next));

            int step = Settings.from <= Settings.to ? 1 : -1; // если "От" больше "До" — считаем назад
            next += step;
            boolean finished = step > 0 ? next > Settings.to : next < Settings.to;
            if (finished) {
                if (Settings.loop) {
                    next = Settings.from;
                } else {
                    running = false;
                    mc.player.displayClientMessage(Component.literal("§eСчёт завершён"), true);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Меню
    // ------------------------------------------------------------------
    static final class MenuScreen extends Screen {
        private final long openedAt = System.currentTimeMillis();

        private DelaySlider slider;
        private Button startButton;
        private Button loopButton;
        private EditBox fromBox;
        private EditBox toBox;

        private int top;
        private int hintY;
        private int fieldsY;
        private int statusY;

        MenuScreen() {
            super(Component.literal("Count Chat"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            top = Math.max(4, (this.height - 178) / 2);
            hintY = top + 61;
            fieldsY = top + 72;
            statusY = top + 168;

            // Ползунок скорости
            slider = this.addRenderableWidget(new DelaySlider(cx - 100, top + 16, 200, 20));

            // Кнопки точной настройки
            int[] deltas = {-10, -1, 1, 10};
            for (int i = 0; i < deltas.length; i++) {
                int d = deltas[i];
                this.addRenderableWidget(Button.builder(
                                Component.literal(d > 0 ? "+" + d : String.valueOf(d)),
                                b -> slider.setTicks(Settings.delayTicks + d))
                        .bounds(cx - 100 + i * 50, top + 38, 48, 20)
                        .build());
            }

            // Поля "От" и "До"
            fromBox = new EditBox(this.font, cx - 80, fieldsY, 78, 20, Component.literal("От"));
            fromBox.setMaxLength(6);
            fromBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}"));
            fromBox.setValue(String.valueOf(Settings.from));
            fromBox.setResponder(s -> {
                if (!s.isEmpty()) {
                    Settings.from = Integer.parseInt(s);
                }
            });
            this.addRenderableWidget(fromBox);

            toBox = new EditBox(this.font, cx + 22, fieldsY, 78, 20, Component.literal("До"));
            toBox.setMaxLength(6);
            toBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}"));
            toBox.setValue(String.valueOf(Settings.to));
            toBox.setResponder(s -> {
                if (!s.isEmpty()) {
                    Settings.to = Integer.parseInt(s);
                }
            });
            this.addRenderableWidget(toBox);

            // Повторять по кругу
            loopButton = this.addRenderableWidget(Button.builder(loopLabel(), b -> {
                Settings.loop = !Settings.loop;
                b.setMessage(loopLabel());
            }).bounds(cx - 100, top + 96, 200, 20).build());

            // Запустить / остановить
            startButton = this.addRenderableWidget(Button.builder(startLabel(), b -> onStartStop())
                    .bounds(cx - 100, top + 120, 200, 20).build());

            // Закрыть
            this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> this.onClose())
                    .bounds(cx - 100, top + 144, 200, 20).build());
        }

        private void onStartStop() {
            Minecraft mc = Minecraft.getInstance();
            if (Counter.isRunning()) {
                Counter.stop();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§cСчёт остановлен"), true);
                }
            } else {
                Counter.start();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§aСчёт запущен"), true);
                }
                this.onClose(); // закрываем меню, чтобы было видно чат
            }
        }

        private static Component startLabel() {
            return Component.literal(Counter.isRunning() ? "§cОстановить" : "§aЗапустить");
        }

        private static Component loopLabel() {
            return Component.literal("Повторять по кругу: " + (Settings.loop ? "§aВкл" : "§cВыкл"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            int cx = this.width / 2;

            g.drawCenteredString(this.font, this.title, cx, top + 2, 0xFFFFFF);

            if (Settings.delayTicks < 20) {
                g.drawCenteredString(this.font,
                        Component.literal("Меньше 20 тиков: возможен кик за спам"), cx, hintY, 0xFFAA00);
            }

            g.drawString(this.font, "От:", cx - 100, fieldsY + 6, 0xFFFFFF);
            g.drawString(this.font, "До:", cx + 4, fieldsY + 6, 0xFFFFFF);

            startButton.setMessage(startLabel());

            if (Counter.isRunning()) {
                g.drawCenteredString(this.font,
                        Component.literal("Идёт счёт, следующее число: " + Counter.getNext()), cx, statusY, 0x55FF55);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            // повторное нажатие U закрывает меню (небольшая защита от залипания клавиши)
            if (CountChat.OPEN_KEY.matches(keyCode, scanCode)
                    && System.currentTimeMillis() - openedAt > 400) {
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false; // мир не ставится на паузу, пока открыто меню
        }

        @Override
        public void removed() {
            Settings.save();
        }

        /** Ползунок задержки: от 1 до 200 тиков. */
        private static final class DelaySlider extends AbstractSliderButton {
            DelaySlider(int x, int y, int width, int height) {
                super(x, y, width, height, Component.empty(), toSlider(Settings.delayTicks));
                updateMessage();
            }

            private static double toSlider(int ticks) {
                int t = Mth.clamp(ticks, Settings.MIN_DELAY, Settings.MAX_DELAY);
                return (t - Settings.MIN_DELAY) / (double) (Settings.MAX_DELAY - Settings.MIN_DELAY);
            }

            private int ticks() {
                return Settings.MIN_DELAY
                        + (int) Math.round(this.value * (Settings.MAX_DELAY - Settings.MIN_DELAY));
            }

            void setTicks(int ticks) {
                this.value = toSlider(ticks);
                applyValue();
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int t = ticks();
                setMessage(Component.literal("Задержка: " + t + " тик. (" + String.format("%.2f", t / 20.0) + " сек)"));
            }

            @Override
            protected void applyValue() {
                Settings.delayTicks = ticks();
            }
        }
    }
}
