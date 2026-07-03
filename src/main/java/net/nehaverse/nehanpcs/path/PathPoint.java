package net.nehaverse.nehanpcs.path;

import org.bukkit.Location;
import org.bukkit.World;

public record PathPoint(String worldName, double x, double y, double z, float yaw, float pitch) {
    public static PathPoint from(Location location) {
        return new PathPoint(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
