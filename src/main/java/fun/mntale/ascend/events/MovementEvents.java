package fun.mntale.ascend.events;

import fun.mntale.ascend.Ascend;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Location;

import java.util.UUID;

public class MovementEvents implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Check if player is already teleporting
        if (Ascend.instance.getPlayerManager().isPlayerTeleporting(uuid)) {
            Location centerPos = Ascend.instance.getPlayerManager().getCenterPosition(uuid);
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
        if (Ascend.instance.getPlayerManager().hasCountdownTask(uuid)) {
            Block standingBlock = player.getLocation().subtract(0, 1, 0).getBlock();
            if (standingBlock.getType() != Material.LODESTONE) {
                Ascend.instance.debug("Player " + uuid + " moved away from lodestone during countdown");
                Ascend.instance.getPlayerManager().cancelCountdown(uuid);
                FoliaScheduler.getRegionScheduler().run(Ascend.instance, player.getLocation(), (t18) -> 
                    player.getLocation().getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.5f)
                );
                FoliaScheduler.getEntityScheduler().runDelayed(player, Ascend.instance, (t19) -> player.removePotionEffect(PotionEffectType.GLOWING),null, 60);

                String message = "§cLaunch cancelled - you moved away from the lodestone!";
                Ascend.instance.debugPlayerMessage(player, message);
            }
            return; // Always return if in countdown, regardless of block type
        }

        // If player is in the process of starting a countdown, ignore this event
        if (Ascend.instance.getPlayerManager().isPlayerStartingCountdown(uuid)) {
            Ascend.instance.debug("Ignoring movement for player " + uuid + " during countdown start");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.COMPASS || !item.hasItemMeta() || !(item.getItemMeta() instanceof CompassMeta meta)) {
            return;
        }

        Block block = player.getLocation().subtract(0, 1, 0).getBlock();
        if (block.getType() != Material.LODESTONE) {
            return;
        }
        // Check source lodestone setup
        String sourceError = Ascend.instance.getPlayerManager().validateLodestoneSetup(player.getLocation().subtract(0, 1, 0));
        if (sourceError != null) {
            Ascend.instance.debug("Invalid source lodestone setup for player " + uuid + ": " + sourceError);
            String message = "§cInvalid source lodestone setup! " + sourceError + ".";
            
            Ascend.instance.debugPlayerMessage(player, message);
            return;
        }

        // Check target lodestone setup
        String targetError = Ascend.instance.getPlayerManager().validateLodestoneSetup(player.getLocation().subtract(0, 1, 0));
        if (targetError != null) {
            Ascend.instance.debug("Invalid target lodestone setup for player " + uuid + ": " + targetError);
            String message = "§cInvalid target lodestone setup! " + targetError + ".";
            
            Ascend.instance.debugPlayerMessage(player, message);
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

            // Check if player is already in any state before proceeding
            if (Ascend.instance.getPlayerManager().isPlayerTeleporting(uuid) || 
                Ascend.instance.getPlayerManager().hasCountdownTask(uuid)) {
                player.sendMessage("§cYou are already in the process of teleporting!");
                return;
            }

            // Mark player as starting countdown
            Ascend.instance.getPlayerManager().addToStartingCountdown(uuid);

            // Check for blocks above both lodestones
            Ascend.instance.getPlayerManager().hasBlocksAbove(block.getLocation()).thenAccept(hasBlocks -> {
                if (hasBlocks) {
                    player.sendMessage("§cThere are blocks above the starting lodestone!");
                    Ascend.instance.getPlayerManager().removeFromStartingCountdown(uuid);
                    return;
                }
                
                Ascend.instance.getPlayerManager().hasBlocksAbove(targetLocation).thenAccept(hasTargetBlocks -> {
                    if (hasTargetBlocks) {
                        player.sendMessage("§cThere are blocks above the target lodestone!");
                        Ascend.instance.getPlayerManager().removeFromStartingCountdown(uuid);
                        return;
                    }
                    
                    // Start countdown
                    Ascend.instance.getPlayerManager().startCountdown(player, uuid, targetLocation, block.getLocation());
                });
            });
        }
    }
} 