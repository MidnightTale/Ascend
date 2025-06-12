package fun.mntale.ascend;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import io.github.retrooper.packetevents.util.folia.TaskWrapper;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class Ascend extends JavaPlugin implements Listener {
    private static Ascend instance;
    private static final Material LODESTONE = Material.LODESTONE;
    private static final Material COMPASS = Material.COMPASS;
    private static final int LAUNCH_HEIGHT = 600;
    private static final int TELEPORT_HEIGHT = 700;
    private final Set<UUID> teleportingPlayers = new HashSet<>();
    private final Map<UUID, Location> centerPositions = new HashMap<>();
    private final Map<UUID, TaskWrapper> countdownTasks = new HashMap<>();
    private final Set<UUID> startingCountdown = new HashSet<>(); // Track players starting countdown

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("Ascend plugin has been enabled!");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player is already teleporting
        if (teleportingPlayers.contains(uuid)) {
            Location centerPos = centerPositions.get(uuid);
            if (centerPos != null) {
                // Calculate velocity to move player to center with dampening
                double dx = (centerPos.getX() - player.getLocation().getX()) * 0.3;
                double dz = (centerPos.getZ() - player.getLocation().getZ()) * 0.3;
                // Apply velocity to keep player centered
                player.setVelocity(player.getVelocity().setX(dx).setZ(dz));
            }
            return;
        }

        // Check if player is in countdown and moved away from lodestone
        TaskWrapper countdownTask = countdownTasks.get(uuid);
        if (countdownTask != null) {
            Block standingBlock = player.getLocation().subtract(0, 1, 0).getBlock();
            if (standingBlock.getType() != LODESTONE) {
                countdownTask.cancel();
                countdownTasks.remove(uuid);
                player.sendMessage("§cLaunch cancelled - you moved away from the lodestone!");
            }
            return; // Always return if in countdown, regardless of block type
        }

        // If player is in the process of starting a countdown, ignore this event
        if (startingCountdown.contains(uuid)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != COMPASS || !item.hasItemMeta() || !(item.getItemMeta() instanceof CompassMeta meta)) {
            return;
        }

        Block block = player.getLocation().subtract(0, 1, 0).getBlock();
        if (block.getType() != LODESTONE) {
            return;
        }

        if (meta.hasLodestone()) {
            Location targetLocation = meta.getLodestone();
            if (targetLocation == null) {
                return;
            }

            if (block.getX() == targetLocation.getBlockX() &&
                block.getY() == targetLocation.getBlockY() &&
                block.getZ() == targetLocation.getBlockZ()) {
                player.sendMessage("§cYou are already at the target location!");
                return;
            }

            // Mark player as starting countdown
            startingCountdown.add(uuid);

            // Check for blocks above both lodestones
            hasBlocksAbove(block.getLocation()).thenAccept(hasBlocks -> {
                if (hasBlocks) {
                    player.sendMessage("§cThere are blocks above the starting lodestone!");
                    startingCountdown.remove(uuid);
                    return;
                }
                
                hasBlocksAbove(targetLocation).thenAccept(hasTargetBlocks -> {
                    if (hasTargetBlocks) {
                        player.sendMessage("§cThere are blocks above the target lodestone!");
                        startingCountdown.remove(uuid);
                        return;
                    }
                    
                    // Only start countdown if not already in one
                    if (!countdownTasks.containsKey(uuid)) {
                        startCountdown(player, uuid, targetLocation, block.getLocation());
                    }
                    startingCountdown.remove(uuid);
                });
            });
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == DamageCause.FALL && player.hasPotionEffect(PotionEffectType.LEVITATION)) {
                event.setCancelled(true);
            }
        }
    }

    private void startCountdown(Player player, UUID uuid, Location targetLocation, Location startLocation) {
        int[] countdown = {3}; // Start from 3
        
        final TaskWrapper[] countdownTask = new TaskWrapper[1];
        countdownTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, this, (t) -> {
            if (countdown[0] > 0) {
                player.sendMessage("§eLaunching in §c" + countdown[0] + "§e...");
                countdown[0]--;
            } else {
                player.sendMessage("§aLaunch!");
                // Store the starting position
                centerPositions.put(uuid, startLocation.add(0.5, 1, 0.5));
                launchPlayer(player, uuid, targetLocation);
                countdownTask[0].cancel();
                countdownTasks.remove(uuid);
            }
        }, null, 20L, 20L); // Run every second (20 ticks)
        
        countdownTasks.put(uuid, countdownTask[0]);
    }

    private void launchPlayer(Player player, UUID uuid, Location targetLocation) {
        teleportingPlayers.add(uuid);
        
        // Store the player's original facing direction
        float originalYaw = player.getLocation().getYaw();
        float originalPitch = player.getLocation().getPitch();
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, -1, 127, false, false));
        
        final TaskWrapper[] taskWrapper = new TaskWrapper[1];
        taskWrapper[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, this, (t) -> {
            double y = player.getLocation().getY();
            
            if (y >= LAUNCH_HEIGHT) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 2 * 20, 0, false, false));
            }
            
            if (y >= TELEPORT_HEIGHT) {
                player.removePotionEffect(PotionEffectType.LEVITATION);
                
                // Create new location with original pitch and yaw
                Location finalLocation = new Location(
                    targetLocation.getWorld(),
                    targetLocation.getX(),
                    TELEPORT_HEIGHT,
                    targetLocation.getZ(),
                    originalYaw,
                    originalPitch
                );
                player.teleport(finalLocation);
                
                // Switch to using target position for centering
                centerPositions.put(uuid, targetLocation.clone().add(0.5, 1, 0.5));
                
                // Start a new task to monitor when player reaches target block
                final TaskWrapper[] arrivalTask = new TaskWrapper[1];
                arrivalTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, this, (t2) -> {
                    Location currentLoc = player.getLocation().subtract(0, 1, 0);
                    if (currentLoc.getBlockX() == targetLocation.getBlockX() &&
                        currentLoc.getBlockY() == targetLocation.getBlockY() &&
                        currentLoc.getBlockZ() == targetLocation.getBlockZ()) {
                        
                        player.sendMessage("§aArrived at destination!");
                        teleportingPlayers.remove(uuid);
                        centerPositions.remove(uuid);
                        arrivalTask[0].cancel();
                    }
                }, null, 1L, 1L);
                
                taskWrapper[0].cancel();
            }
        }, null, 1L, 4L);
    }

    private CompletableFuture<Boolean> hasBlocksAbove(Location location) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        FoliaScheduler.getRegionScheduler().run(this, location, (t3) -> {
            Location checkLoc = location.clone();
            int buildLimit = location.getWorld().getMaxHeight();
            
            for (int y = location.getBlockY() + 1; y < buildLimit; y++) {
                checkLoc.setY(y);
                if (checkLoc.getBlock().getType().isSolid()) {
                    future.complete(true);
                    return;
                }
            }
            future.complete(false);
        });
        
        return future;
    }

    @Override
    public void onDisable() {
        teleportingPlayers.clear();
        centerPositions.clear();
        countdownTasks.values().forEach(TaskWrapper::cancel);
        countdownTasks.clear();
        startingCountdown.clear();
        getLogger().info("Ascend plugin has been disabled!");
    }
}
