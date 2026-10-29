
package id.server.maceclaim;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaceClaim extends JavaPlugin
        implements Listener, CommandExecutor {

    private NamespacedKey maceKey;
    private boolean active;
    private long endTime;
    private UUID lastHolder;
    private long lastAnnouncement;
    private BossBar bossBar;

    @Override
    public void onEnable() {
        maceKey = new NamespacedKey(this, "contest_mace");

        active = getConfig().getBoolean("event.active", false);
        endTime = getConfig().getLong("event.end-time", 0L);

        bossBar = Bukkit.createBossBar(
            "Mace Claim", BarColor.PURPLE, BarStyle.SOLID
        );
        bossBar.setVisible(false);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("mace").setExecutor(this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!active) {
                bossBar.setVisible(false);
                return;
            }

            if (System.currentTimeMillis() >= endTime) {
                finishEvent();
                return;
            }

            Player holder = findHolder();
            long remaining = Math.max(
                0L, endTime - System.currentTimeMillis()
            );

            if (holder == null) {
                bossBar.setTitle(
                    "§dMACE CLAIM §8| §cTidak ada pemegang"
                    + " §8| §e" + formatTime(remaining)
                );

                if (lastHolder != null) {
                    Bukkit.broadcastMessage(
                        "§c[Mace] Mace tidak sedang dipegang!"
                    );
                }

                lastHolder = null;
            } else {
                Location loc = holder.getLocation();
                World world = loc.getWorld();

                bossBar.setTitle(
                    "§dMACE §8| §f" + holder.getName()
                    + " §8| §b" + world.getName()
                    + " §8X: §e" + loc.getBlockX()
                    + " §8Y: §e" + loc.getBlockY()
                    + " §8Z: §e" + loc.getBlockZ()
                    + " §8| §a" + formatTime(remaining)
                );

                if (!holder.getUniqueId().equals(lastHolder)) {
                    Bukkit.broadcastMessage(
                        "§6[Mace] §e" + holder.getName()
                        + " §fsekarang memegang Mace!"
                    );
                }

                lastHolder = holder.getUniqueId();
            }

            bossBar.setProgress(
                Math.max(0.0, Math.min(1.0,
                    (double) remaining / (45L * 60L * 1000L)))
            );
            bossBar.setVisible(true);

            if (System.currentTimeMillis() - lastAnnouncement >= 60000L) {
                Bukkit.broadcastMessage(
                    "§6[Mace] §fSisa waktu: §e" + formatTime(remaining)
                );
                lastAnnouncement = System.currentTimeMillis();
            }
        }, 20L, 20L);

        if (active && endTime <= System.currentTimeMillis()) {
            finishEvent();
        }

        getLogger().info("MaceClaim berhasil dimuat.");
    }

    @Override
    public void onDisable() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        saveState();
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                showHelp(sender);
                return true;

            case "start":
                if (!isAdmin(sender)) return true;

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                        "Pegang Mace di game lalu jalankan /mace start."
                    );
                    return true;
                }

                if (active) {
                    sender.sendMessage("§cEvent sudah berjalan.");
                    return true;
                }

                ItemStack held = player.getInventory().getItemInMainHand();

                if (held.getType() != Material.MACE) {
                    sender.sendMessage(
                        "§cPegang Mace di tangan utama terlebih dahulu!"
                    );
                    return true;
                }

                markMace(held);

                active = true;
                endTime = System.currentTimeMillis()
                    + 45L * 60L * 1000L;
                lastHolder = null;
                lastAnnouncement = System.currentTimeMillis();
                saveState();

                for (Player online : Bukkit.getOnlinePlayers()) {
                    bossBar.addPlayer(online);
                }

                Bukkit.broadcastMessage(
                    "§6§l[MACE] §ePEREBUTAN MACE DIMULAI!"
                );
                Bukkit.broadcastMessage(
                    "§fWaktu: §c45 menit. §fKoordinat pemegang tampil di Boss Bar."
                );
                return true;

            case "stop":
                if (!isAdmin(sender)) return true;

                active = false;
                endTime = 0L;
                lastHolder = null;
                clearAllTags();
                bossBar.setVisible(false);
                saveState();

                Bukkit.broadcastMessage("§c[Mace] Event dihentikan.");
                return true;

            case "status":
                if (!active) {
                    sender.sendMessage("§cEvent tidak aktif.");
                    return true;
                }

                sender.sendMessage(
                    "§6Sisa waktu: §e"
                    + formatTime(endTime - System.currentTimeMillis())
                );

                Player current = findHolder();

                sender.sendMessage(
                    current == null
                        ? "§cMace tidak sedang dipegang."
                        : "§fPemegang: §e" + current.getName()
                );
                return true;

            case "locate":
                if (!active) {
                    sender.sendMessage("§cEvent tidak aktif.");
                    return true;
                }

                Player holder = findHolder();

                if (holder == null) {
                    sender.sendMessage("§cTidak ada pemegang Mace.");
                } else {
                    Location loc = holder.getLocation();

                    sender.sendMessage(
                        "§6Pemegang: §e" + holder.getName()
                        + " §f| Dunia: §b" + loc.getWorld().getName()
                        + " §f| X: §e" + loc.getBlockX()
                        + " §fY: §e" + loc.getBlockY()
                        + " §fZ: §e" + loc.getBlockZ()
                    );
                }
                return true;

            case "reset":
                if (!isAdmin(sender)) return true;

                active = false;
                endTime = 0L;
                lastHolder = null;
                clearAllTags();
                bossBar.setVisible(false);
                saveState();

                Bukkit.broadcastMessage("§c[Mace] Event direset.");
                return true;

            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission("maceclaim.admin")) return true;

        sender.sendMessage("§cKamu tidak punya izin.");
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6===== MACE CLAIM =====");
        sender.sendMessage("§e/mace start §7- Mulai event");
        sender.sendMessage("§e/mace stop §7- Hentikan event");
        sender.sendMessage("§e/mace status §7- Cek status");
        sender.sendMessage("§e/mace locate §7- Cek koordinat");
        sender.sendMessage("§e/mace reset §7- Reset event");
        sender.sendMessage("§e/mace help §7- Bantuan");
    }

    private void markMace(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();

        if (meta == null) return;

        meta.getPersistentDataContainer().set(
            maceKey, PersistentDataType.BYTE, (byte) 1
        );
        stack.setItemMeta(meta);
    }

    private boolean isContestMace(ItemStack stack) {
        if (stack == null || stack.getType() != Material.MACE) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();

        return meta != null
            && meta.getPersistentDataContainer().has(
                maceKey, PersistentDataType.BYTE
            );
    }

    private Player findHolder() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            if (isContestMace(inv.getItemInMainHand())
                    || isContestMace(inv.getItemInOffHand())) {
                return player;
            }

            for (ItemStack stack : inv.getContents()) {
                if (isContestMace(stack)) return player;
            }
        }

        return null;
    }

    @EventHandler
    public void onMacePickup(EntityPickupItemEvent event) {
        if (!active || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!isContestMace(event.getItem().getItemStack())) return;

        Bukkit.getScheduler().runTask(this, () -> {
            if (active && player.isOnline()) {
                bossBar.addPlayer(player);
            }
        });
    }

    private String formatTime(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void saveState() {
        getConfig().set("event.active", active);
        getConfig().set("event.end-time", endTime);
        saveConfig();
    }

    private void finishEvent() {
        Player winner = findHolder();

        active = false;
        endTime = 0L;
        lastHolder = null;
        clearAllTags();
        bossBar.setVisible(false);
        saveState();

        if (winner != null) {
            Bukkit.broadcastMessage(
                "§6§l[MACE] §e" + winner.getName()
                + " §fmenjadi pemilik Mace!"
            );
        } else {
            Bukkit.broadcastMessage(
                "§c[MACE] Waktu habis tanpa pemegang Mace."
            );
        }
    }

    private void clearAllTags() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            for (ItemStack stack : inv.getContents()) {
                removeTag(stack);
            }

            removeTag(inv.getItemInOffHand());
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();

                if (isContestMace(stack)) {
                    removeTag(stack);
                    item.setItemStack(stack);
                }
            }
        }
    }

    private void removeTag(ItemStack stack) {
        if (!isContestMace(stack)) return;

        ItemMeta meta = stack.getItemMeta();

        if (meta == null) return;

        meta.getPersistentDataContainer().remove(maceKey);
        stack.setItemMeta(meta);
    }
        }
                
