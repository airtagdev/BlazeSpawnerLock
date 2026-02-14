package org.frh.blazespawnerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Iterator;

public class BlazeSpawnerLock extends JavaPlugin implements Listener {

    /* ===============================
       UPDATE SETTINGS (CHECK ONLY)
       =============================== */
    private static final String VERSION_URL =
            "https://github.com/airtagdev/BlazeSpawnerLock/releases/latest/download/version.txt";

    private static final String RELEASE_URL =
            "https://github.com/airtagdev/BlazeSpawnerLock/releases/latest";

    // 6 hours in ticks
    private static final long UPDATE_CHECK_INTERVAL = 6L * 60L * 60L * 20L;

    /* ===============================
       MESSAGE PREFIX (IN-GAME ONLY)
       =============================== */
    private static final String GAME_PREFIX =
            ChatColor.translateAlternateColorCodes(
                    '&',
                    "&l[&c&lBlaze&7&lSpawner&c&lLock&r&l] "
            );

    private static final String BYPASS_PERMISSION = "blazespawnerlock.bypass";
    private static final String RELOAD_PERMISSION = "blazespawnerlock.reload";

    private boolean enabled;
    private boolean checkForUpdate;
    private String denyMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin successfully loaded");

        // bStats
        int pluginId = 29525;
        new Metrics(this, pluginId);

        if (checkForUpdate) {
            // Immediate check
            checkForUpdates();

            // Then every 6 hours
            getServer().getScheduler().runTaskTimerAsynchronously(
                    this,
                    this::checkForUpdates,
                    UPDATE_CHECK_INTERVAL,
                    UPDATE_CHECK_INTERVAL
            );
        }
    }

    /* ===============================
       CONFIG
       =============================== */
    private void loadConfig() {
        reloadConfig();

        enabled = getConfig().getBoolean("enabled", true);
        checkForUpdate = getConfig().getBoolean("check-for-update", true);

        denyMessage = ChatColor.translateAlternateColorCodes(
                '&',
                getConfig().getString(
                        "messages.deny",
                        "&l[&c&lBlaze&7&lSpawner&c&lLock&r&l] &cYou cannot break blaze spawners!"
                )
        );
    }

    /* ===============================
       COMMANDS
       =============================== */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage("§cYou do not have permission to do this.");
                return true;
            }

            loadConfig();
            sender.sendMessage("§aBlazeSpawnerLock config reloaded.");
            return true;
        }

        sender.sendMessage("§cUsage: /" + label + " reload");
        return true;
    }

    /* ===============================
       BREAK PROTECTION
       =============================== */
    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (!enabled) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) return;

        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        if (spawner.getSpawnedType() == EntityType.BLAZE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(denyMessage);

            try {
                Sound sound = Sound.valueOf("ENTITY_VILLAGER_NO");
                event.getPlayer().playSound(
                        event.getPlayer().getLocation(),
                        sound,
                        1.0f,
                        1.0f
                );
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /* ===============================
       EXPLOSION PROTECTION
       =============================== */
    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (!enabled) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();

            if (block.getType() != Material.SPAWNER) continue;

            CreatureSpawner spawner = (CreatureSpawner) block.getState();

            if (spawner.getSpawnedType() == EntityType.BLAZE) {
                iterator.remove();
            }
        }
    }

    /* ===============================
       UPDATE HANDLER
       =============================== */
    private void checkForUpdates() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new URL(VERSION_URL).openStream())
            );

            String remoteVersion = reader.readLine().trim();
            reader.close();

            String localVersion = getDescription().getVersion();

            if (remoteVersion.equals(localVersion)) {
                getLogger().info("Plugin is up to date.");
                return;
            }

            getLogger().warning("Plugin is out of date. A newer version is available.");

            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.isOp())
                        .forEach(p -> {
                            TextComponent msg = new TextComponent(
                                    GAME_PREFIX + "&6A new version is available. Click &bhere &6to download."
                            );
                            msg.setClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.OPEN_URL,
                                            RELEASE_URL
                                    )
                            );
                            p.spigot().sendMessage(msg);
                        });
            });

        } catch (Exception e) {
            getLogger().warning("Failed to check for updates.");
        }
    }
}
