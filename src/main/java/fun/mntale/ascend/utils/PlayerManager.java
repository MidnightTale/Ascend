package fun.mntale.ascend.utils;

import org.bukkit.Location;
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
import net.kyori.adventure.text.Component;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;

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
        Ascend.instance.debug("Cancelling countdown for player " + uuid);
        
        TaskWrapper task = countdownTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            Ascend.instance.debug("Cancelled countdown task for player " + uuid);
        }
        
        // Also remove from starting countdown if present
        boolean wasInCountdown = startingCountdown.remove(uuid);
        if (wasInCountdown) {
            Ascend.instance.debug("Removed player " + uuid + " from starting countdown");
        }
        
        // Clear teleporting state
        boolean wasTeleporting = teleportingPlayers.remove(uuid);
        if (wasTeleporting) {
            Ascend.instance.debug("Removed player " + uuid + " from teleporting state");
        }
        
        centerPositions.remove(uuid);
        
        // Debug log final state
        Ascend.instance.debug("Final state after cancellation for player " + uuid + ":");
        Ascend.instance.debug("Is teleporting: " + teleportingPlayers.contains(uuid));
        Ascend.instance.debug("Has countdown task: " + countdownTasks.containsKey(uuid));
        Ascend.instance.debug("Is starting countdown: " + startingCountdown.contains(uuid));
    }

    public boolean isPlayerStartingCountdown(UUID uuid) {
        return startingCountdown.contains(uuid);
    }

    public void addToStartingCountdown(UUID uuid) {
        startingCountdown.add(uuid);
        Ascend.instance.debug("Added player " + uuid + " to starting countdown");
    }

    public void removeFromStartingCountdown(UUID uuid) {
        boolean removed = startingCountdown.remove(uuid);
        if (removed) {
            Ascend.instance.debug("Removed player " + uuid + " from starting countdown");
        }
    }

    public boolean removeTeleportingPlayer(UUID uuid) {
        boolean removed = teleportingPlayers.remove(uuid);
        if (removed) {
            centerPositions.remove(uuid);
            Ascend.instance.debug("Removed player " + uuid + " from teleporting state");
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

    public String validateLodestoneSetup(Location lodestoneLoc) {
        StringBuilder missingBeacons = new StringBuilder();
        StringBuilder inactiveBeacons = new StringBuilder();
        // Check the 3x3 area around the lodestone
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location checkLoc = lodestoneLoc.clone().add(x, 0, z);
                // Skip the center (lodestone position)
                if (x == 0 && z == 0) continue;
                
                // Check if it's a corner position (should be beacon)
                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    var block = checkLoc.getBlock();
                    if (block.getType() != org.bukkit.Material.BEACON) {
                        String corner = getCornerName(x, z);
                        if (!missingBeacons.isEmpty()) {
                            missingBeacons.append(", ");
                        }
                        missingBeacons.append(corner);
                    } else {
                        // Check if beacon is active by checking if it's powered
                        var beaconState = (org.bukkit.block.Beacon) block.getState();
                        if (beaconState.getTier() <= 0) {
                            String corner = getCornerName(x, z);
                            if (!inactiveBeacons.isEmpty()) {
                                inactiveBeacons.append(", ");
                            }
                            inactiveBeacons.append(corner);
                        }
                    }
                }
            }
        }
        
        StringBuilder error = new StringBuilder();
        if (!missingBeacons.isEmpty()) {
            error.append("Missing beacons in the ").append(missingBeacons).append(" corners");
        }
        if (!inactiveBeacons.isEmpty()) {
            if (!error.isEmpty()) {
                error.append(" and ");
            }
            error.append("inactive beacons in the ").append(inactiveBeacons).append(" corners");
        }
        
        return !error.isEmpty() ? error.toString() : null;
    }

    private String getCornerName(int x, int z) {
        if (x == -1 && z == -1) return "northwest";
        if (x == 1 && z == -1) return "northeast";
        if (x == -1 && z == 1) return "southwest";
        if (x == 1 && z == 1) return "southeast";
        return "";
    }

    public void startCountdown(Player player, UUID uuid, Location targetLocation, Location startLocation) {
        // Debug log current states
        Ascend.instance.debug("Checking states for player " + uuid);
        Ascend.instance.debug("Is teleporting: " + teleportingPlayers.contains(uuid));
        Ascend.instance.debug("Has countdown task: " + countdownTasks.containsKey(uuid));
        Ascend.instance.debug("Is starting countdown: " + startingCountdown.contains(uuid));

        // Check source lodestone setup
        String sourceError = validateLodestoneSetup(startLocation);
        if (sourceError != null) {
            // Clean up all states
            cancelCountdown(uuid);
            startingCountdown.remove(uuid);
            teleportingPlayers.remove(uuid);
            centerPositions.remove(uuid);
            String message = "§cInvalid source lodestone setup! " + sourceError + ".";
            Ascend.instance.debugPlayerMessage(player, message);
            return;
        }

        // Check target lodestone setup
        String targetError = validateLodestoneSetup(targetLocation);
        if (targetError != null) {
            // Clean up all states
            cancelCountdown(uuid);
            startingCountdown.remove(uuid);
            teleportingPlayers.remove(uuid);
            centerPositions.remove(uuid);
            String message = "§cInvalid target lodestone setup! " + targetError + ".";
            Ascend.instance.debugPlayerMessage(player, message);
            return;
        }

        // Safety check - if player is already teleporting or has a countdown task, cancel it and return
        if (teleportingPlayers.contains(uuid) || countdownTasks.containsKey(uuid)) {
            // Clean up any existing states
            cancelCountdown(uuid);
            String message = "§cYou are already in the process of teleporting!";
            Ascend.instance.debugPlayerMessage(player, message);
            return;
        }

        // Create the countdown task
        int[] countdown = {3}; // Start from 3
        final TaskWrapper[] countdownTask = new TaskWrapper[1];
        
        // Create the task first
        countdownTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t1) -> {
            // Safety check - if player is no longer online or dead, cancel task
            if (!player.isOnline() || player.isDead()) {
                Ascend.instance.debug("Player " + uuid + (player.isDead() ? " died" : " went offline") + " during countdown");
                countdownTask[0].cancel();
                countdownTasks.remove(uuid);
                teleportingPlayers.remove(uuid);
                centerPositions.remove(uuid);
                startingCountdown.remove(uuid);
                return;
            }

            if (countdown[0] > 0) {
                String message = "§eLaunching in §c" + countdown[0] + "§e...";
                Ascend.instance.debugPlayerMessage(player, message);
                // Play countdown sound and effects at lodestone location using RegionScheduler
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, startLocation, (t2) -> {
                    startLocation.getWorld().playSound(startLocation, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 1.6f);
                    ParticleEffects.spawnCountdownParticles(startLocation);
                });
                countdown[0]--;
            } else {
                String message = "§aLaunch!";
                Ascend.instance.debugPlayerMessage(player, message);
                // Play launch sound and effects at lodestone location using RegionScheduler
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, startLocation, (t3) -> {
                    targetLocation.getWorld().playSound(targetLocation, org.bukkit.Sound.BLOCK_PISTON_EXTEND, 1.2f, 0.8f);
                    targetLocation.getWorld().playSound(targetLocation, org.bukkit.Sound.ITEM_TRIDENT_THROW, 0.8f, 0.7f);
                    startLocation.getWorld().playSound(startLocation, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.2f);
                });
                // Store the starting position
                centerPositions.put(uuid, startLocation.add(0.5, 1, 0.5));
                launchPlayer(player, uuid, targetLocation);
                countdownTask[0].cancel();
                countdownTasks.remove(uuid);
                startingCountdown.remove(uuid);
                Ascend.instance.debug("Countdown completed for player " + uuid);
            }
        }, null, 20L, 20L); // Run every second (20 ticks)

        // Only add to states after task is created and all checks have passed
        countdownTasks.put(uuid, countdownTask[0]);
        Ascend.instance.debug("Started countdown task for player " + uuid);
    }

    private void launchPlayer(Player player, UUID uuid, Location targetLocation) {
        // Safety check - if player is no longer online, don't proceed
        if (!player.isOnline()) {
            teleportingPlayers.remove(uuid);
            centerPositions.remove(uuid);
            return;
        }

        // Calculate distance and required Eyes of Ender
        Location currentLocEnder = player.getLocation();
        double distance = currentLocEnder.distance(targetLocation);
        int requiredEyes = (int) Math.ceil(distance / 1000.0) * 2 - 1; // Formula: (distance/1000) * 2 - 1
        requiredEyes = Math.max(1, requiredEyes); // Minimum 1 eye

        // Count available Eyes of Ender
        int availableEyes = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.ENDER_EYE) {
                availableEyes += item.getAmount();
            }
        }

        if (availableEyes < requiredEyes) {
            String message = "Launch cancelled - you need " + requiredEyes + " Eye" + (requiredEyes > 1 ? "s" : "") + " of Ender to launch this distance!";
            player.sendActionBar(Component.text(message));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            cancelCountdown(uuid);
            removeFromStartingCountdown(uuid);
            teleportingPlayers.remove(uuid);
            centerPositions.remove(uuid);
            return;
        }

        // Remove required Eyes of Ender
        int remainingToRemove = requiredEyes;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.ENDER_EYE) {
                if (item.getAmount() <= remainingToRemove) {
                    remainingToRemove -= item.getAmount();
                    item.setAmount(0);
                } else {
                    item.setAmount(item.getAmount() - remainingToRemove);
                    remainingToRemove = 0;
                }
                if (remainingToRemove <= 0) break;
            }
        }

        // Play sound for consuming Eyes of Ender
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, (float) 0.8, 1.0f);

        // Show cost in action bar
        String costMessage = "Used " + requiredEyes + " Eye" + (requiredEyes > 1 ? "s" : "") + " of Ender for this launch";
        player.sendActionBar(Component.text(costMessage));

        teleportingPlayers.add(uuid);
        
        // Create explosion effect at launch - use RegionScheduler for particle effects
        FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t4) -> {
            // Spawn launch explosion particles
            ParticleEffects.spawnLaunchExplosion(player.getLocation());
            // Apply harming effect instead of direct damage
            FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t878) -> {
                player.damage(4.0);
            }, null);
            Ascend.instance.debug("Player " + uuid + " took launch effect");
        });
        
        // Add levitation effect - use EntityScheduler for entity operations
        FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t5) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, -1, 127, false, false));
        },null);
            
        
        final TaskWrapper[] taskWrapper = new TaskWrapper[1];
        taskWrapper[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t6) -> {
            // Safety check - if player is no longer online or dead, cancel task and clean up
            if (!player.isOnline() || player.isDead()) {
                taskWrapper[0].cancel();
                teleportingPlayers.remove(uuid);
                centerPositions.remove(uuid);
                if (player.isDead()) {
                    Ascend.instance.debug("Player " + uuid + " died during teleportation");
                }
                return;
            }

            double y = player.getLocation().getY();
            
            // Check and destroy blocks in player's path
            FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t7) -> {
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
                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t8) -> player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 2 * 20, 0, false, false)), null);
            }
            
            if (y >= Ascend.TELEPORT_HEIGHT) {
                // Remove levitation effect - use EntityScheduler for entity operations
                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t9) -> player.removePotionEffect(PotionEffectType.LEVITATION), null);
                
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
                
                // Spawn portal particles and effects at both locations
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t10) -> {
                    ParticleEffects.spawnSinglePortalEffect(player.getLocation());
                    player.getLocation().getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_TELEPORT, 0.2f, 1.7f);
                    player.getLocation().getWorld().strikeLightningEffect(player.getLocation());
    
                });

                FoliaScheduler.getRegionScheduler().run(Ascend.instance, finalLocation, (t10) -> {
                    ParticleEffects.spawnSinglePortalEffect(finalLocation);
                    finalLocation.getWorld().playSound(finalLocation, org.bukkit.Sound.ENTITY_PLAYER_TELEPORT, 0.2f, 0.4f);
                    finalLocation.getWorld().strikeLightningEffect(finalLocation);

                });

                if (player.isOnline() && !player.isDead()) {
                    player.teleportAsync(finalLocation);
                    
                    // Switch to using target position for centering
                    centerPositions.put(uuid, targetLocation.clone().add(0.5, 1, 0.5));
                    
                    // Start monitoring for arrival
                    final TaskWrapper[] arrivalTask = new TaskWrapper[1];
                    arrivalTask[0] = FoliaScheduler.getEntityScheduler().runAtFixedRate(player, Ascend.instance, (t11) -> {
                        if (!player.isOnline() || player.isDead()) {
                            arrivalTask[0].cancel();
                            teleportingPlayers.remove(uuid);
                            centerPositions.remove(uuid);
                            if (player.isDead()) {
                                Ascend.instance.debug("Player " + uuid + " died during landing");
                            }
                            return;
                        }

                        // Check if target lodestone still exists
                        if (targetLocation.getBlock().getType() != Material.LODESTONE) {
                            Ascend.instance.debug("Target lodestone was destroyed during teleportation for player " + uuid);
                            String message = "§cTarget lodestone was destroyed! Returning to launch location...";
                            Ascend.instance.debugPlayerMessage(player, message);
                            
                            // Cancel current task and clean up all states
                            arrivalTask[0].cancel();
                            cancelCountdown(uuid);
                            removeFromStartingCountdown(uuid);
                            teleportingPlayers.remove(uuid);
                            centerPositions.remove(uuid);
                            
                            // Teleport back to launch location
                            Location launchLoc = centerPositions.get(uuid);
                            if (launchLoc != null) {
                                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t42) -> {
                                    player.teleportAsync(launchLoc);
                                    player.removePotionEffect(PotionEffectType.LEVITATION);
                                    // Play failure sound
                                    launchLoc.getWorld().playSound(launchLoc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.5f);
                                    // Spawn failure particles
                                    ParticleEffects.spawnLaunchExplosion(launchLoc);
                                }, null);
                            }
                            return;
                        }

                        // Check and destroy blocks in player's path during descent
                        FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t12) -> {
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
                                    blockLoc.getWorld().playSound(blockLoc, soundGroup.getBreakSound(), 0.6f, 1.3f);
                                    // Drop the block items
                                    blockLoc.getBlock().breakNaturally();
                                }
                            }
                        });

                        Location currentLoc = player.getLocation().subtract(0, 1, 0);
                        if (currentLoc.getBlockX() == targetLocation.getBlockX() &&
                            currentLoc.getBlockY() == targetLocation.getBlockY() &&
                            currentLoc.getBlockZ() == targetLocation.getBlockZ()) {
                            
                            String message = "§aArrived at destination!";
                            Ascend.instance.debugPlayerMessage(player, message);
                            // Play arrival sound and effects
                            FoliaScheduler.getEntityScheduler().runDelayed(player, Ascend.instance, (t13) -> player.removePotionEffect(PotionEffectType.GLOWING),null, 60);

                            FoliaScheduler.getRegionScheduler().run(Ascend.instance, targetLocation, (t14) -> {
                                targetLocation.getWorld().playSound(targetLocation, org.bukkit.Sound.ITEM_TOTEM_USE, 0.8f, 0.7f);
                                
                                // Apply landing damage before removing teleporting state
                                FoliaScheduler.getEntityScheduler().run(player, Ascend.instance, (t18) -> {
                                    player.damage(4.0); // 2 hearts of damage
                                }, null);
                                Ascend.instance.debug("Player " + uuid + " took landing damage");
                                
                                // Keep player in teleporting state for a moment after landing
                                FoliaScheduler.getEntityScheduler().runDelayed(player, Ascend.instance, (t15) -> {
                                    teleportingPlayers.remove(uuid);
                                    centerPositions.remove(uuid);
                                    arrivalTask[0].cancel();
                                }, null, 10L); // 4 tick delay before removing teleporting state
                                
                                // Spawn end rod particles in an explosion pattern
                                Location center = targetLocation.clone().add(0.5, 0.5, 0.5);
                                for (int i = 0; i < 50; i++) {
                                    double angle = Math.random() * Math.PI * 2;
                                    double radius = Math.random() * 3;
                                    double x = center.getX() + (radius * Math.cos(angle));
                                    double z = center.getZ() + (radius * Math.sin(angle));
                                    double particleY = center.getY() + (Math.random() * 2 - 1);
                                    
                                    Location particleLoc = new Location(
                                        center.getWorld(),
                                        x,
                                        particleY,
                                        z
                                    );
                                    
                                    center.getWorld().spawnParticle(
                                        org.bukkit.Particle.END_ROD,
                                        particleLoc,
                                        1, // count
                                        0, // offsetX
                                        0, // offsetY
                                        0, // offsetZ
                                        0.1 // speed
                                    );
                                }
                            });
                            arrivalTask[0].cancel();
                        }
                    }, null, 1L, 1L);
                }
                
                taskWrapper[0].cancel();
            }
        }, null, 1L, 1L);
    }
} 