package dev.rangefinder.betterheimerlite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class BetterHeimerLiteClient implements ClientModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("betterheimerlite");
    private static final AtomicBoolean SCANNING = new AtomicBoolean(false);
    private static final AtomicBoolean CANCEL_REQUESTED = new AtomicBoolean(false);
    private static final AtomicInteger CHECKED_HOSTS = new AtomicInteger(0);
    private static final AtomicInteger FOUND_SERVERS = new AtomicInteger(0);
    private static final AtomicInteger ADDED_SERVERS = new AtomicInteger(0);
    private static volatile int totalHosts = 0;
    private static volatile String status = "Idle. Open BH Config to set a private IPv4 range.";

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // Available on the title screen; scans can be launched without loading a world.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;

            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            widgets.add(Button.builder(Component.literal("BH Scan"), button -> {
                client.setScreen(new BetterHeimerScreen(screen));
                ModConfig config = ModConfig.load();
                if (!startScan(config)) setStatus("A scan is already running.");
            }).bounds(8, 8, 100, 20).build());

            widgets.add(Button.builder(Component.literal("BH Config"), button ->
                    client.setScreen(new BetterHeimerScreen(screen)))
                    .bounds(8, 32, 100, 20).build());
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext) -> dispatcher.register(
                ClientCommands.literal("betterheimer")
                        .then(ClientCommands.literal("scan").executes(context -> {
                            ModConfig config = ModConfig.load();
                            if (!startScan(config)) {
                                context.getSource().sendFeedback(Component.literal("BetterHeimer Lite is already scanning."));
                                return 0;
                            }
                            context.getSource().sendFeedback(Component.literal("Started BetterHeimer Lite scan."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("cancel").executes(context -> {
                            if (!cancelScan()) {
                                context.getSource().sendFeedback(Component.literal("No scan is running."));
                                return 0;
                            }
                            context.getSource().sendFeedback(Component.literal("Cancellation requested."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reload").executes(context -> {
                            ModConfig config = ModConfig.load();
                            setStatus("Reloaded range " + config.range + ", port " + config.port + ".");
                            context.getSource().sendFeedback(Component.literal("Reloaded BetterHeimer Lite config."));
                            return 1;
                        }))
        ));
        LOGGER.info("BetterHeimer Lite loaded. Title-screen buttons: BH Scan and BH Config.");
    }

    static String getStatus() {
        return status;
    }

    static boolean isScanning() {
        return SCANNING.get();
    }

    static String getProgressText() {
        int total = totalHosts;
        int checked = CHECKED_HOSTS.get();
        int percent = total <= 0 ? 0 : Math.min(100, (int) ((checked * 100L) / total));
        return "Progress: " + percent + "% (" + checked + "/" + total + ") | Found: "
                + FOUND_SERVERS.get() + " | Saved: " + ADDED_SERVERS.get();
    }

    static float getProgressFraction() {
        int total = totalHosts;
        if (total <= 0) return 0.0f;
        return Math.min(1.0f, CHECKED_HOSTS.get() / (float) total);
    }

    static void setStatus(String message) {
        status = message == null ? "" : message;
    }

    static boolean startScan(ModConfig config) {
        if (!SCANNING.compareAndSet(false, true)) return false;
        CANCEL_REQUESTED.set(false);
        CHECKED_HOSTS.set(0);
        FOUND_SERVERS.set(0);
        ADDED_SERVERS.set(0);
        totalHosts = 0;
        setStatus("Starting scan for " + config.range + "...");
        Thread scanner = new Thread(() -> scan(config), "BetterHeimer-Lite-Scanner");
        scanner.setDaemon(true);
        scanner.start();
        return true;
    }

    static boolean cancelScan() {
        if (!SCANNING.get()) return false;
        CANCEL_REQUESTED.set(true);
        setStatus("Cancellation requested; finishing in-flight status checks...");
        return true;
    }

    private static void scan(ModConfig config) {
        String endMessage;
        ExecutorService pool = null;
        List<Future<ScanResult>> tasks = new ArrayList<>();
        int submitted = 0;

        try {
            List<String> hosts = Ipv4Cidr.expand(config.range, config.maxHosts);
            totalHosts = hosts.size();
            updateProgressStatus(config.range);

            pool = Executors.newFixedThreadPool(config.parallelism, runnable -> {
                Thread thread = new Thread(runnable, "BetterHeimer-Lite-Ping");
                thread.setDaemon(true);
                return thread;
            });
            CompletionService<ScanResult> completedTasks = new ExecutorCompletionService<>(pool);

            // Stagger launches by delayMs, while allowing up to parallelism sockets in flight.
            for (String host : hosts) {
                if (CANCEL_REQUESTED.get()) break;
                tasks.add(completedTasks.submit(() -> scanHost(host, config)));
                submitted++;
                if (config.delayMs > 0 && submitted < hosts.size() && !sleepBetweenLaunches(config.delayMs)) break;
            }

            int received = 0;
            while (received < submitted && !CANCEL_REQUESTED.get()) {
                Future<ScanResult> completed = completedTasks.poll(100, TimeUnit.MILLISECONDS);
                if (completed == null) continue;
                received++;
                try {
                    completed.get();
                } catch (ExecutionException exception) {
                    LOGGER.error("A host scan failed unexpectedly.", exception.getCause());
                } catch (java.util.concurrent.CancellationException ignored) {
                    // Expected when a scan is cancelled.
                }
            }

            if (CANCEL_REQUESTED.get()) {
                for (Future<ScanResult> task : tasks) task.cancel(true);
                endMessage = "Scan cancelled. Checked " + CHECKED_HOSTS.get() + "/" + totalHosts
                        + " host(s); found " + FOUND_SERVERS.get() + "; newly saved " + ADDED_SERVERS.get() + ".";
            } else {
                endMessage = "Scan finished. Checked " + CHECKED_HOSTS.get() + "/" + totalHosts
                        + " host(s); found " + FOUND_SERVERS.get() + "; newly saved " + ADDED_SERVERS.get() + ".";
            }
        } catch (IllegalArgumentException exception) {
            endMessage = "Scan not started: " + exception.getMessage();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            endMessage = "Scan interrupted. Checked " + CHECKED_HOSTS.get() + "; found " + FOUND_SERVERS.get() + ".";
        } catch (Exception exception) {
            LOGGER.error("Unexpected scanner error.", exception);
            endMessage = "Scan stopped unexpectedly. See latest.log for details.";
        } finally {
            if (pool != null) pool.shutdownNow();
        }

        String finalMessage = endMessage;
        Minecraft.getInstance().execute(() -> {
            setStatus(finalMessage);
            notifyClient(finalMessage);
            SCANNING.set(false);
            CANCEL_REQUESTED.set(false);
        });
    }

    private static boolean sleepBetweenLaunches(int delayMs) throws InterruptedException {
        int remaining = delayMs;
        while (remaining > 0 && !CANCEL_REQUESTED.get()) {
            int slice = Math.min(remaining, 50);
            Thread.sleep(slice);
            remaining -= slice;
        }
        return !CANCEL_REQUESTED.get();
    }

    private static ScanResult scanHost(String host, ModConfig config) throws InterruptedException {
        Optional<String> version = MinecraftStatusPing.queryVersion(host, config.port, config.timeoutMs);
        boolean saved = false;
        if (version.isPresent()) {
            FoundServer found = new FoundServer(host, config.port, version.get());
            FOUND_SERVERS.incrementAndGet();
            try {
                // Serialize the server-list write on Minecraft's main thread and wait for it,
                // so the server is persisted as soon as its status response is received.
                saved = persistFoundServerImmediately(found);
                if (saved) ADDED_SERVERS.incrementAndGet();
                LOGGER.info("Minecraft server found: {}:{} ({}){}", host, config.port, version.get(),
                        saved ? " [saved to servers.dat]" : " [already in server list]");
            } catch (ExecutionException exception) {
                LOGGER.error("Found {}:{} but could not save it to the vanilla server list.", host, config.port,
                        exception.getCause());
            }
        }
        CHECKED_HOSTS.incrementAndGet();
        updateProgressStatus(config.range);
        return new ScanResult(host, version.isPresent(), saved);
    }

    private static boolean persistFoundServerImmediately(FoundServer found)
            throws InterruptedException, ExecutionException {
        CompletableFuture<Boolean> persisted = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                persisted.complete(addToVanillaServerList(List.of(found)) > 0);
            } catch (Throwable throwable) {
                persisted.completeExceptionally(throwable);
            }
        });
        return persisted.get();
    }

    private static void updateProgressStatus(String range) {
        status = (CANCEL_REQUESTED.get() ? "Cancelling" : "Scanning") + " " + range + " — "
                + CHECKED_HOSTS.get() + "/" + totalHosts + " checked; " + FOUND_SERVERS.get()
                + " found; " + ADDED_SERVERS.get() + " newly saved.";
    }

    private static int addToVanillaServerList(List<FoundServer> found) {
        if (found.isEmpty()) return 0;
        Minecraft minecraft = Minecraft.getInstance();
        ServerList serverList = new ServerList(minecraft);
        serverList.load();

        Set<String> existingAddresses = new HashSet<>();
        for (int i = 0; i < serverList.size(); i++) {
            ServerData existing = serverList.get(i);
            existingAddresses.add(normalizeAddress(existing.ip));
        }

        int added = 0;
        for (FoundServer server : found) {
            String address = server.host + ":" + server.port;
            if (!existingAddresses.add(normalizeAddress(address))) continue;
            String version = server.version.length() > 32 ? server.version.substring(0, 32) : server.version;
            String name = "BH Lite | " + server.host + " | " + version;
            if (name.length() > 64) name = name.substring(0, 64);
            serverList.add(new ServerData(name, address, ServerData.Type.OTHER), false);
            added++;
        }
        if (added > 0) serverList.save();
        return added;
    }

    private static String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private static void notifyClient(String message) {
        LOGGER.info("{}", message);
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    private record FoundServer(String host, int port, String version) { }
    private record ScanResult(String host, boolean found, boolean saved) { }
}
