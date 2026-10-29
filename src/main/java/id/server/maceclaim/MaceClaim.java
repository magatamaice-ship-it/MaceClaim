package id.server.maceclaim;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
    private long lastCoordinateAnnouncement;

    @Override
    public void onEnable() {
        maceKey = new NamespacedKey(this, "contest_mace");

        active = getConfig().getBoolean("event.active", false);
        endTime = getConfig().getLong("event.end-time", 0L);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("mace").setExecutor(this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!active) return;

            if (System.currentTimeMillis() >= endTime) {
                finishEvent();
                return;
            }

            Player holder = findHolder();

            UUID current = holder == null ? null : holder.getUniqueId();

            if (current == null && lastHolder != null) {
                Bukkit.broadcastMessage(
                    "§c[Mace] Mace jatuh atau tidak sedang dipegang! Timer tetap berjalan."
                );
            }

            if (current != null && !current.equals(lastHolder)) {
                Bukkit.broadcastMessage(
                    "§6[Mace] §e" + holder.getName()
                    + " §fsekarang memegang Mace!"
                );
                announceCoordinates(holder);
            }

            lastHolder = current;

            long now = System.currentTimeMillis();

            if (holder != null && now - lastCoordinateAnnouncement >= 30000L) {
                announceCoordinates(holder);
                lastCoordinateAnnouncement = now;
            }

            if (now - lastAnnouncement >= 60000L) {
                long minutes = Math.max(
                    0L, (endTime - now + 59999L) / 60000L
                );

                Bukkit.broadcastMessage(
                    "§6[Mace] §fSisa waktu perebutan: §e"
                    + minutes + " menit."
                );

                lastAnnouncement = now;
            }
        }, 20L, 20L);

        if (active && endTime <= System.currentTimeMillis()) {
            finishEvent();
        } else if (active) {
            Bukkit.broadcastMessage(
                "§6[Mace] Event dilanjutkan setelah server menyala kembali."
            );
        }

        getLogger().info("MaceClaim berhasil dimuat.");
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

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                showHelp(sender);
                return true;

            case "start":
                if (!isAdmin(sender)) return true;

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(
                        "Pegang Mace di dalam game, lalu jalankan /mace start."
                    );
                    return true;
                }

                if (active) {
                    sender.sendMessage("§cEvent Mace sudah berjalan.");
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
                endTime = System.currentTimeMillis() + 45L * 60L * 1000L;
                lastHolder = null;
                lastAnnouncement = System.currentTimeMillis();
                lastCoordinateAnnouncement = 0L;
                saveState();

                Bukkit.broadcastMessage(
                    "§6§l[MACE] §ePEREBUTAN MACE DIMULAI!"
                );
                Bukkit.broadcastMessage(
                    "§fWaktu perebutan: §c45 menit."
                );
                Bukkit.broadcastMessage(
                    "§fPemegang Mace akan diumumkan beserta koordinatnya."
                );
                return true;

            case "stop":
                if (!isAdmin(sender)) return true;

                if (!active) {
                    sender.sendMessage("§cTidak ada event yang berjalan.");
                    return true;
                }

                active = false;
                endTime = 0L;
                clearAllTags();
                saveState();

                Bukkit.broadcastMessage("§c[Mace] Event dihentikan admin.");
                return true;

            case "status":
                sender.sendMessage("§6===== STATUS MACE =====");

                if (!active) {
                    sender.sendMessage("§cEvent tidak aktif.");
                    return true;
                }

                long remaining = Math.max(
                    0L, (endTime - System.currentTimeMillis()) / 1000L
                );

                sender.sendMessage(
                    "§fSisa waktu: §e" + (remaining / 60)
                    + " menit " + (remaining % 60) + " detik"
                );

                Player currentHolder = findHolder();

                if (currentHolder == null) {
                    sender.sendMessage("§fPemegang: §cTidak ada");
                } else {
                    sender.sendMessage(
                        "§fPemegang: §e" + currentHolder.getName()
                    );
                }
                return true;

            case "locate":
                if (!active) {
                    sender.sendMessage("§cEvent Mace tidak aktif.");
                    return true;
                }

                Player holder = findHolder();

                if (holder == null) {
                    sender.sendMessage(
                        "§cMace sedang tidak dipegang pemain."
                    );
                } else {
                    sendCoordinates(sender, holder);
                }
                return true;

            case "reset":
                if (!isAdmin(sender)) return true;

                active = false;
                endTime = 0L;
                lastHolder = null;
                clearAllTags();
                saveState();

                Bukkit.broadcastMessage("§c[Mace] Event telah direset.");
                return true;

            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission("maceclaim.admin")) {
            return true;
        }

        sender.sendMessage("§cKamu tidak memiliki izin untuk melakukan itu.");
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6===== MACE CLAIM =====");
        sender.sendMessage("§e/mace start §7- Mulai perebutan 45 menit");
        sender.sendMessage("§e/mace stop §7- Hentikan event");
        sender.sendMessage("§e/mace status §7- Cek status dan waktu");
        sender.sendMessage("§e/mace locate §7- Cek koordinat pemegang");
        sender.sendMessage("§e/mace reset §7- Reset event");
        sender.sendMessage("§e/mace help §7- Tampilkan bantuan");
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
            PlayerInventory inventory = player.getInventory();

            if (isContestMace(inventory.getItemInMainHand())
                    || isContestMace(inventory.getItemInOffHand())) {
                return player;
            }

            for (ItemStack stack : inventory.getContents()) {
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

        ItemStack stack = event.getItem().getItemStack();

        if (isContestMace(stack)) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (active && isOnline(player)) {
                    announceCoordinates(player);
                    lastCoordinateAnnouncement =
                        System.currentTimeMillis();
                }
            });
        }
    }

    private boolean isOnline(Player player) {
        return player.isOnline();
    }

    private void announceCoordinates(Player player) {
        Location location = player.getLocation();

        Bukkit.broadcastMessage(
            "§6[Mace] §e" + player.getName()
            + " §fmemegang Mace di §b"
            + location.getWorld().getName()
            + " §f(X: §e" + location.getBlockX()
            + "§f, Y: §e" + location.getBlockY()
            + "§f, Z: §e" + location.getBlockZ() + "§f)"
        );
    }

    private void sendCoordinates(CommandSender sender, Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();

        sender.sendMessage(
            "§6[Mace] §e" + player.getName()
            + " §fberada di §b" + world.getName()
            + " §f(X: §e" + location.getBlockX()
            + "§f, Y: §e" + location.getBlockY()
            + "§f, Z: §e" + location.getBlockZ() + "§f)"
        );
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
        saveState();

        if (winner != null) {
            Bukkit.broadcastMessage(
                "§6§l[MACE] §e" + winner.getName()
                + " §fmenjadi pemilik Mace!"
            );
        } else {
            Bukkit.broadcastMessage(
                "§c[MACE] Waktu habis, tetapi tidak ada pemain yang memegang Mace."
            );
        }
    }

    private void clearAllTags() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();

            for (ItemStack stack : inventory.getContents()) {
                removeTag(stack);
            }

            removeTag(inventory.getItemInOffHand());
            removeTag(inventory.getItemInMainHand());
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item entity : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = entity.getItemStack();

                if (isContestMace(stack)) {
                    removeTag(stack);
                    entity.setItemStack(stack);
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
                         
