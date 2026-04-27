package org.teighto.ghostBlockFix;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Syncer {

    private final JavaPlugin plugin;

    public Syncer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncPlayersNear(Collection<Location> rawLocations, double distance) {
        Set<Location> locations = normalize(rawLocations);
        if (locations.isEmpty()) {
            return;
        }

        Location anchor = locations.iterator().next();
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }

        double maxDistanceSq = distance * distance;
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= maxDistanceSq) {
                viewers.add(player);
            }
        }

        for (Player player : viewers) {
            syncPlayer(player, locations);
        }
    }

    public void syncCubeForPlayer(Player player, int horizontalRadius, int down, int up) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        Set<Location> locations = new HashSet<>();
        int baseX = base.getBlockX();
        int baseY = base.getBlockY();
        int baseZ = base.getBlockZ();

        for (int x = baseX - horizontalRadius; x <= baseX + horizontalRadius; x++) {
            for (int y = baseY - down; y <= baseY + up; y++) {
                for (int z = baseZ - horizontalRadius; z <= baseZ + horizontalRadius; z++) {
                    locations.add(new Location(world, x, y, z));
                }
            }
        }

        syncPlayer(player, locations);
    }

    public void syncLocationsForPlayer(Player player, Collection<Location> rawLocations) {
        syncPlayer(player, normalize(rawLocations));
    }

    public void runNextTick(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private void syncPlayer(Player player, Collection<Location> locations) {
        for (Location location : locations) {
            World world = location.getWorld();
            if (world == null || !world.equals(player.getWorld())) {
                continue;
            }

            Block block = world.getBlockAt(location);
            Location blockLocation = block.getLocation();
            player.sendBlockChange(blockLocation, block.getBlockData());
        }
    }

    private Set<Location> normalize(Collection<Location> rawLocations) {
        Set<Location> normalized = new HashSet<>();
        for (Location location : rawLocations) {
            if (location == null || location.getWorld() == null) {
                continue;
            }
            normalized.add(new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            ));
        }
        return normalized;
    }
}
