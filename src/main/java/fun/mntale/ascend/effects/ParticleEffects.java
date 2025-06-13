package fun.mntale.ascend.effects;

import org.bukkit.Location;
import org.bukkit.Particle;

public class ParticleEffects {
    
    public static void spawnCountdownParticles(Location location) {
        // Center the location on the block
        Location centerLoc = location.clone().add(0.5, 0, 0.5);
        
        // Number of particles in the circle
        int particles = 12;
        // Radius of the circle
        double radius = 0.5;
        
        for (int i = 0; i < particles; i++) {
            double angle = (2 * Math.PI * i) / particles;
            
            // Calculate position for each particle in the circle
            double x = centerLoc.getX() + radius * Math.cos(angle);
            double z = centerLoc.getZ() + radius * Math.sin(angle);
            
            // Create 3 levels of particles going up
            for (int y = 0; y < 3; y++) {
                Location particleLoc = new Location(
                    centerLoc.getWorld(),
                    x,
                    centerLoc.getY() + y,
                    z
                );
                
                // Spawn end rod particle
                centerLoc.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1, // count
                    0, // offsetX
                    0, // offsetY
                    0, // offsetZ
                    0  // speed
                );
            }
        }
    }

    public static void spawnLaunchExplosion(Location location) {
        // Center the location on the block
        Location centerLoc = location.clone().add(0.5, 0, 0.5);
        
        // Create multiple layers of particles
        for (int layer = 0; layer < 3; layer++) {
            // Number of particles increases with each layer
            int particles = 24 + (layer * 12);
            // Radius increases with each layer
            double radius = 0.5 + (layer * 0.5);
            
            for (int i = 0; i < particles; i++) {
                double angle = (2 * Math.PI * i) / particles;
                
                // Calculate position for each particle
                double x = centerLoc.getX() + radius * Math.cos(angle);
                double z = centerLoc.getZ() + radius * Math.sin(angle);
                
                // Random height variation for explosion effect
                double y = centerLoc.getY() + (Math.random() * 2);
                
                Location particleLoc = new Location(
                    centerLoc.getWorld(),
                    x,
                    y,
                    z
                );
                
                // Spawn end rod particle with upward velocity
                centerLoc.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1, // count
                    0, // offsetX
                    0.5, // offsetY (upward velocity)
                    0, // offsetZ
                    0.2 // speed
                );
            }
        }
        
        // Add a burst of particles straight up
        for (int i = 0; i < 30; i++) {
            Location burstLoc = centerLoc.clone().add(
                (Math.random() - 0.5) * 0.5,
                0,
                (Math.random() - 0.5) * 0.5
            );
            
            centerLoc.getWorld().spawnParticle(
                Particle.END_ROD,
                burstLoc,
                1,
                0,
                1,
                0,
                0.5
            );
        }
    }

    public static void spawnSinglePortalEffect(Location location) {
        // Center the location on the block
        Location centerLoc = location.clone().add(0.5, 0, 0.5);
        
        // Create a single burst of portal particles
        for (int i = 0; i < 3; i++) { // Three layers
            double radius = 0.5 + (i * 0.2); // Slightly different radius for each layer
            int particles = 12; // Number of particles in circle
            
            for (int j = 0; j < particles; j++) {
                double angle = (2 * Math.PI * j) / particles;
                
                // Calculate position for each particle
                double x = centerLoc.getX() + radius * Math.cos(angle);
                double z = centerLoc.getZ() + radius * Math.sin(angle);
                
                // Create particles at different heights
                for (int h = 0; h < 2; h++) {
                    Location particleLoc = new Location(
                        centerLoc.getWorld(),
                        x,
                        centerLoc.getY() + h,
                        z
                    );
                    
                    // Spawn portal particle
                    centerLoc.getWorld().spawnParticle(
                        Particle.PORTAL,
                        particleLoc,
                        1, // count
                        0, // offsetX
                        0, // offsetY
                        0, // offsetZ
                        0  // speed
                    );
                }
            }
        }
        
        // Add a small burst of particles in the center
        for (int i = 0; i < 8; i++) {
            Location randomLoc = centerLoc.clone().add(
                (Math.random() - 0.5) * 0.3,
                Math.random() * 1.5,
                (Math.random() - 0.5) * 0.3
            );
            
            centerLoc.getWorld().spawnParticle(
                Particle.PORTAL,
                randomLoc,
                1,
                0,
                0,
                0,
                0
            );
        }
    }
} 