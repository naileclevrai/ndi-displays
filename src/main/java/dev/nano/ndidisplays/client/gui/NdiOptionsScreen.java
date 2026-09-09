package dev.nano.ndidisplays.client.gui;

import dev.nano.ndidisplays.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The mod's options page: how far from the player a camera keeps sending, whether that limit is
 * off altogether, and the way through to the drone controller bindings. Every change is written
 * to the client config at once, so it survives a relaunch without a save button.
 */
public class NdiOptionsScreen extends Screen {

    private final Screen parent;
    private RangeSlider rangeSlider;

    public NdiOptionsScreen(Screen parent) {
        super(Component.translatable("gui.ndidisplays.options.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        int y = 40;

        rangeSlider = addRenderableWidget(new RangeSlider(left, y, 320, 20));
        rangeSlider.active = !ClientConfig.CAMERA_RANGE_UNLIMITED.get();
        y += 24;

        addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.CAMERA_RANGE_UNLIMITED.get())
                .create(left, y, 320, 20, Component.translatable("gui.ndidisplays.options.range_unlimited"),
                        (b, val) -> {
                            ClientConfig.CAMERA_RANGE_UNLIMITED.set(val);
                            ClientConfig.SPEC.save();
                            rangeSlider.active = !val;
                        }));
        y += 36;

        addRenderableWidget(Button.builder(Component.translatable("gui.ndidisplays.pad.open"),
                b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new DronePadOptionsScreen(this));
                    }
                })
                .bounds(left, y, 320, 20).build());
        y += 36;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 78, y, 156, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.ndidisplays.options.range_hint"),
                width / 2, 104, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** Camera cutoff distance in blocks, on a 16-step grid between the config's bounds. */
    private static final class RangeSlider extends AbstractSliderButton {

        private static final int MIN = ClientConfig.CAMERA_RANGE_MIN;
        private static final int MAX = ClientConfig.CAMERA_RANGE_MAX;

        RangeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                    (ClientConfig.CAMERA_RANGE.get() - MIN) / (double) (MAX - MIN));
            updateMessage();
        }

        private int blocks() {
            int raw = MIN + (int) Math.round(value * (MAX - MIN));
            return Mth.clamp(Math.round(raw / 16.0F) * 16, MIN, MAX);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.ndidisplays.options.range", blocks()));
        }

        @Override
        protected void applyValue() {
            ClientConfig.CAMERA_RANGE.set(blocks());
            ClientConfig.SPEC.save();
        }
    }
}
