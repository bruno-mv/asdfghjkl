package dev.rangefinder.betterheimerlite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Config and scan controls which also work from the title screen. */
final class BetterHeimerScreen extends Screen {
    private final Screen parent;
    private EditBox rangeField;
    private EditBox portField;
    private EditBox timeoutField;
    private EditBox delayField;
    private EditBox maxHostsField;
    private EditBox parallelismField;

    BetterHeimerScreen(Screen parent) {
        super(Component.literal("BetterHeimer Lite"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.load();
        int center = this.width / 2;
        int labelX = Math.max(8, center - 174);
        int inputX = center - 18;
        int fieldWidth = Math.max(105, Math.min(210, this.width - inputX - 16));
        int firstY = 50;
        int rowGap = 24;

        rangeField = addField(inputX, firstY, fieldWidth, "192.168.1.0/24", config.range, 40);
        portField = addField(inputX, firstY + rowGap, fieldWidth, "25565", String.valueOf(config.port), 5);
        timeoutField = addField(inputX, firstY + rowGap * 2, fieldWidth, "700", String.valueOf(config.timeoutMs), 4);
        delayField = addField(inputX, firstY + rowGap * 3, fieldWidth, "25", String.valueOf(config.delayMs), 4);
        maxHostsField = addField(inputX, firstY + rowGap * 4, fieldWidth, "1024", String.valueOf(config.maxHosts), 4);
        parallelismField = addField(inputX, firstY + rowGap * 5, fieldWidth, "8", String.valueOf(config.parallelism), 2);

        int buttonY = this.height - 28;
        int buttonGap = 4;
        int buttonWidth = Math.min(78, Math.max(62, (this.width - 24 - buttonGap * 3) / 4));
        int totalWidth = buttonWidth * 4 + buttonGap * 3;
        int startX = (this.width - totalWidth) / 2;

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveConfigFromFields())
                .bounds(startX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Start scan"), button -> {
            ModConfig configFromFields = saveConfigFromFields();
            if (configFromFields != null && !BetterHeimerLiteClient.startScan(configFromFields)) {
                BetterHeimerLiteClient.setStatus("A scan is already running.");
            }
        }).bounds(startX + buttonWidth + buttonGap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            if (!BetterHeimerLiteClient.cancelScan()) {
                BetterHeimerLiteClient.setStatus("No scan is running.");
            }
        }).bounds(startX + (buttonWidth + buttonGap) * 2, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(startX + (buttonWidth + buttonGap) * 3, buttonY, buttonWidth, 20).build());
    }

    private EditBox addField(int x, int y, int width, String placeholder, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.literal(placeholder));
        box.setValue(value);
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    private ModConfig saveConfigFromFields() {
        try {
            int port = parseInt("Port", portField.getValue(), 1, 65535);
            int timeout = parseInt("Timeout", timeoutField.getValue(), 200, 3000);
            int delay = parseInt("Launch delay", delayField.getValue(), 0, 5000);
            int maxHosts = parseInt("Max hosts", maxHostsField.getValue(), 1, ModConfig.HARD_MAX_HOSTS);
            int parallelism = parseInt("Parallel workers", parallelismField.getValue(), 1, 16);
            ModConfig config = new ModConfig(rangeField.getValue().trim(), port, timeout, delay, maxHosts, parallelism);

            // Validate range and the configured limit before persisting or starting a scan.
            Ipv4Cidr.expand(config.range, config.maxHosts);
            config.save();
            BetterHeimerLiteClient.setStatus("Settings saved — " + config.range + ", port " + config.port + ".");
            return config;
        } catch (IllegalArgumentException exception) {
            BetterHeimerLiteClient.setStatus("Config error: " + exception.getMessage());
            return null;
        }
    }

    private static int parseInt(String label, String text, int min, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String heading = "BetterHeimer Lite";
        graphics.text(this.font, heading, this.width / 2 - this.font.width(heading) / 2, 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "Private / authorized IPv4 only — max 4096 hosts; parallel checks: 1–16.",
                Math.max(8, this.width / 2 - 190), 29, 0xFFFFFFFF, true);

        int labelX = Math.max(8, this.width / 2 - 174);
        int firstY = 55;
        int rowGap = 24;
        graphics.text(this.font, "IP range (CIDR)", labelX, firstY, 0xFFFFFFFF, true);
        graphics.text(this.font, "Port", labelX, firstY + rowGap, 0xFFFFFFFF, true);
        graphics.text(this.font, "Timeout (ms)", labelX, firstY + rowGap * 2, 0xFFFFFFFF, true);
        graphics.text(this.font, "Launch delay (ms)", labelX, firstY + rowGap * 3, 0xFFFFFFFF, true);
        graphics.text(this.font, "Max hosts", labelX, firstY + rowGap * 4, 0xFFFFFFFF, true);
        graphics.text(this.font, "Parallel workers", labelX, firstY + rowGap * 5, 0xFFFFFFFF, true);

        int barLeft = 10;
        int barRight = Math.max(barLeft + 1, this.width - 10);
        int barTop = this.height - 81;
        int barBottom = barTop + 9;
        graphics.fill(barLeft, barTop, barRight, barBottom, 0xFF343434);
        int filledRight = barLeft + Math.round((barRight - barLeft) * BetterHeimerLiteClient.getProgressFraction());
        if (filledRight > barLeft) graphics.fill(barLeft, barTop, filledRight, barBottom, 0xFF43B86C);
        String progress = BetterHeimerLiteClient.getProgressText();
        int maxProgressChars = Math.max(16, (this.width - 20) / 6);
        if (progress.length() > maxProgressChars) progress = progress.substring(0, maxProgressChars - 3) + "...";
        graphics.text(this.font, progress, 10, this.height - 68, 0xFFFFFFFF, true);

        String status = BetterHeimerLiteClient.getStatus();
        int maxChars = Math.max(24, (this.width - 20) / 6);
        if (status.length() > maxChars) status = status.substring(0, maxChars - 3) + "...";
        graphics.text(this.font, status, 10, this.height - 48, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
