package fun.mntale.ascend.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import fun.mntale.ascend.Ascend;

public class DamageEvents implements Listener {
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Cancel all damage if player is teleporting
            if (Ascend.instance.getPlayerManager().isPlayerTeleporting(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // Cancel countdown if player takes damage
            if (Ascend.instance.getPlayerManager().hasCountdownTask(player.getUniqueId())) {
                Ascend.instance.getPlayerManager().cancelCountdown(player.getUniqueId());
                player.sendMessage("§cCountdown cancelled due to damage!");
            }
        }
    }
} 