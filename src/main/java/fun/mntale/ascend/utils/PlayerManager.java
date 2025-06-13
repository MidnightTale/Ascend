package fun.mntale.ascend.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import fun.mntale.ascend.Ascend;
import fun.mntale.ascend.effects.ParticleEffects;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import io.github.retrooper.packetevents.util.folia.TaskWrapper;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Particle;

public class PlayerManager {
    private final Set<UUID> teleportingPlayers = new CopyOnWriteArraySet<>();
    private final Map<UUID, Location> centerPositions = new ConcurrentHashMap<>();
    private final Map<UUID, TaskWrapper> countdownTasks = new ConcurrentHashMap<>();
    private final Set<UUID> startingCountdown = new CopyOnWriteArraySet<>();
    private final Map<UUID, Location> storedLaunchLocations = new ConcurrentHashMap<>();

    public boolean isPlayerTeleporting(UUID uuid) {
        return teleportingPlayers.contains(uuid);
    }

    public Location getCenterPosition(UUID uuid) {
        return centerPositions.get(uuid);
    }

    public boolean hasCountdownTask(UUID uuid) {
        return countdownTasks.containsKey(uuid);
    }

    public void cancelCountdown(UUID uuid) {
        Ascend.instance.getLogger().info("Cancelling countdown for player " + uuid);
        
        TaskWrapper task = countdownTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            Ascend.instance.getLogger().info("Cancelled countdown task for player " + uuid);
        }
        
        // Also remove from starting countdown if present
        boolean wasInCountdown = startingCountdown.remove(uuid);
        if (wasInCountdown) {
            Ascend.instance.getLogger().info("Removed player " + uuid + " from starting countdown");
        }
        
        // Clear teleporting state
        boolean wasTeleporting = teleportingPlayers.remove(uuid);
        if (wasTeleporting) {
            Ascend.instance.getLogger().info("Removed player " + uuid + " from teleporting state");
        }
        
        centerPositions.remove(uuid);
        
        // Debug log final state
        Ascend.instance.getLogger().info("Final state after cancellation for player " + uuid + ":");
        Ascend.instance.getLogger().info("Is teleporting: " + teleportingPlayers.contains(uuid));
        Ascend.instance.getLogger().info("Has countdown task: " + countdownTasks.containsKey(uuid));
        Ascend.instance.getLogger().info("Is starting countdown: " + startingCountdown.contains(uuid));
    }

    public boolean isPlayerStartingCountdown(UUID uuid) {
        return startingCountdown.contains(uuid);
    }

    public void addToStartingCountdown(UUID uuid) {
        startingCountdown.add(uuid);
        Ascend.instance.getLogger().info("Added player " + uuid + " to starting countdown");
    }

    public void removeFromStartingCountdown(UUID uuid) {
        boolean removed = startingCountdown.remove(uuid);
        if (removed) {
            Ascend.instance.getLogger().info("Removed player " + uuid + " from starting countdown");
        }
    }

    public boolean removeTeleportingPlayer(UUID uuid) {
        boolean removed = teleportingPlayers.remove(uuid);
        if (removed) {
            centerPositions.remove(uuid);
            Ascend.instance.getLogger().info("Removed player " + uuid + " from teleporting state");
        }
        return removed;
    }

    public void removeCenterPosition(UUID uuid) {
        centerPositions.remove(uuid);
    }

    public void storeLaunchLocation(UUID uuid, Location location) {
        storedLaunchLocations.put(uuid, location);
    }

    public Location getStoredLaunchLocation(UUID uuid) {
        return storedLaunchLocations.remove(uuid);
    }

    public CompletableFuture<Boolean> hasBlocksAbove(Location location) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Use RegionScheduler for block operations
        FoliaScheduler.getRegionScheduler().run(Ascend.instance, location, (t3) -> {
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

    public void startCountdown(Player player, UUID uuid, Location targetLocation, Location startLocation) {
        // Debug log current states
        Ascend.instance.getLogger().info("Checking states for player " + uuid);
        Ascend.instance.getLogger().info("Is teleporting: " + teleportingPlayers.contains(uuid));
        Ascend.instance.getLogger().info("Has countdown task: " + countdownTasks.containsKey(uuid));
        Ascend.instance.getLogger().info("Is starting countdown: " + startingCountdown.contains(uuid));

        // Safety check - if player is already teleporting or has a countdown task, cancel it and return
        if (teleportingPlayers.contains(uuid) || countdownTasks.containsKey(uuid)) {
            // Clean up any existing states
            cancelCountdown(uuid);
            player.sendMessage("§cYou are already in the process of teleporting!");
            return;
        }

        // Create the countdown task
        int[] countdown = {3}; // Start from 3
        final TaskWrapper[] countdownTask = new TaskWrapper[1];
        
        // Create the task first
        countdownTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t) -> {
            // Safety check - if player is no longer online, cancel task
            if (!player.isOnline()) {
                Ascend.instance.getLogger().info("Player " + uuid + " went offline during countdown");
                countdownTask[0].cancel();
                countdownTasks.remove(uuid);
                teleportingPlayers.remove(uuid);
                centerPositions.remove(uuid);
                startingCountdown.remove(uuid);
                return;
            }

            if (countdown[0] > 0) {
                player.sendMessage("§eLaunching in §c" + countdown[0] + "§e...");
                // Spawn particles during countdown - use RegionScheduler for particle effects
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, startLocation, (t2) -> ParticleEffects.spawnCountdownParticles(startLocation));
                countdown[0]--;
            } else {
                player.sendMessage("§aLaunch!");
                // Store the starting position
                centerPositions.put(uuid, startLocation.add(0.5, 1, 0.5));
                launchPlayer(player, uuid, targetLocation);
                countdownTask[0].cancel();
                countdownTasks.remove(uuid);
                startingCountdown.remove(uuid);
                Ascend.instance.getLogger().info("Countdown completed for player " + uuid);
            }
        }, null, 20L, 20L); // Run every second (20 ticks)

        // Only add to states after task is created and all checks have passed
        countdownTasks.put(uuid, countdownTask[0]);
        Ascend.instance.getLogger().info("Started countdown task for player " + uuid);
    }

    private void launchPlayer(Player player, UUID uuid, Location targetLocation) {
        // Safety check - if player is no longer online, don't proceed
        if (!player.isOnline()) {
            teleportingPlayers.remove(uuid);
            centerPositions.remove(uuid);
            return;
        }

        teleportingPlayers.add(uuid);
        
        // Create explosion effect at launch - use RegionScheduler for particle effects
        FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t) -> ParticleEffects.spawnLaunchExplosion(player.getLocation()));
        
        // Add levitation effect - use EntityScheduler for entity operations
        FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t) -> player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, -1, 127, false, false)), null);
        
        final TaskWrapper[] taskWrapper = new TaskWrapper[1];
        taskWrapper[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t) -> {
            // Safety check - if player is no longer online, cancel task and clean up
            if (!player.isOnline()) {
                taskWrapper[0].cancel();
                teleportingPlayers.remove(uuid);
                centerPositions.remove(uuid);
                return;
            }

            double y = player.getLocation().getY();
            
            // Check and destroy blocks in player's path
            FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t2) -> {
                Location playerLoc = player.getLocation();
                // Check blocks in a 1x16x1 column above the player
                for (int height = 0; height <= 16; height++) {
                    Location blockLoc = playerLoc.clone().add(0, height, 0);
                    if (blockLoc.getBlock().getType().isSolid()) {
                        // Get block data and sound before breaking
                        var blockData = blockLoc.getBlock().getBlockData();
                        var soundGroup = blockData.getSoundGroup();
                        // Play block break sound
                        blockLoc.getWorld().playSound(blockLoc, soundGroup.getBreakSound(), 1.0f, 1.0f);
                        // Drop the block items
                        blockLoc.getBlock().breakNaturally();
                    }
                }
            });
            
            if (y >= Ascend.LAUNCH_HEIGHT) {
                // Add darkness effect - use EntityScheduler for entity operations
                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t2) -> player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 2 * 20, 0, false, false)), null);
            }
            
            if (y >= Ascend.TELEPORT_HEIGHT) {
                // Remove levitation effect - use EntityScheduler for entity operations
                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t2) -> player.removePotionEffect(PotionEffectType.LEVITATION), null);
                
                // Create new location with original pitch and yaw
                float originalYaw = player.getLocation().getYaw();
                float originalPitch = player.getLocation().getPitch();
                
                Location finalLocation = new Location(
                    targetLocation.getWorld(),
                    targetLocation.getX(),
                    Ascend.TELEPORT_HEIGHT,
                    targetLocation.getZ(),
                    originalYaw,
                    originalPitch
                );
                
                // Spawn portal particles at both locations - use RegionScheduler for particle effects
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t2) -> ParticleEffects.spawnSinglePortalEffect(player.getLocation()));
                
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, finalLocation, (t2) -> ParticleEffects.spawnSinglePortalEffect(finalLocation));
                
                if (player.isOnline()) {
                    player.teleportAsync(finalLocation);
                    
                    // Switch to using target position for centering
                    centerPositions.put(uuid, targetLocation.clone().add(0.5, 1, 0.5));
                    
                    // Start monitoring for arrival
                    final TaskWrapper[] arrivalTask = new TaskWrapper[1];
                    arrivalTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t2) -> {
                        if (!player.isOnline()) {
                            arrivalTask[0].cancel();
                            teleportingPlayers.remove(uuid);
                            centerPositions.remove(uuid);
                            return;
                        }

                        // Check and destroy blocks in player's path during descent
                        FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t3) -> {
                            Location playerLoc = player.getLocation();
                            // Check blocks in a 1x16x1 column below the player, but stop at target lodestone level
                            for (int height = 0; height >= -16; height--) {
                                Location blockLoc = playerLoc.clone().add(0, height, 0);
                                // Stop if we've reached the target lodestone's Y level
                                if (blockLoc.getBlockY() <= targetLocation.getBlockY()) {
                                    break;
                                }
                                if (blockLoc.getBlock().getType().isSolid()) {
                                    // Get block data and sound before breaking
                                    var blockData = blockLoc.getBlock().getBlockData();
                                    var soundGroup = blockData.getSoundGroup();
                                    // Play block break sound
                                    blockLoc.getWorld().playSound(blockLoc, soundGroup.getBreakSound(), 1.0f, 1.0f);
                                    // Drop the block items
                                    blockLoc.getBlock().breakNaturally();
                                }
                            }
                        });

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
                }
                
                taskWrapper[0].cancel();
            }
        }, null, 1L, 4L);
    }
} 