package org.teighto.ghostBlockFix;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Events implements Listener {

    private static final long MOVE_SYNC_COOLDOWN_MS = 250L;
    private static final long FALLBACK_SYNC_COOLDOWN_MS = 3_500L;
    private static final long DIRTY_CHUNK_TTL_MS = 8_000L;
    private static final long CLEANUP_INTERVAL_MS = 3_000L;
    private static final long PLAYER_STATE_TTL_MS = 60_000L;

    private final JavaPlugin plugin;
    private final Syncer syncer;
    private final Map<String, Long> dirtyChunks = new HashMap<>();
    private final Map<UUID, Long> lastMoveSyncAt = new HashMap<>();
    private final Map<UUID, Long> lastFallbackSyncAt = new HashMap<>();
    private long nextCleanupAt;

    public Events(JavaPlugin plugin, Syncer syncer) {
        this.plugin = plugin;
        this.syncer = syncer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Location> changed = new HashSet<>();
        changed.add(event.getBlock().getLocation());

        for (Block block : event.getBlocks()) {
            changed.add(block.getLocation());
            changed.add(block.getRelative(event.getDirection()).getLocation());
            addNeighbours(changed, block.getLocation());
        }

        markDirty(changed);
        resyncAround(changed, 40.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Location> changed = new HashSet<>();
        changed.add(event.getBlock().getLocation());

        for (Block block : event.getBlocks()) {
            changed.add(block.getLocation());
            changed.add(block.getRelative(event.getDirection().getOppositeFace()).getLocation());
            addNeighbours(changed, block.getLocation());
        }

        markDirty(changed);
        resyncAround(changed, 40.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Set<Location> changed = new HashSet<>();
        changed.add(event.getBlockPlaced().getLocation());
        addNeighbours(changed, event.getBlockPlaced().getLocation());
        markDirty(changed);
        resyncAround(changed, 32.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Set<Location> changed = new HashSet<>();
        changed.add(event.getBlock().getLocation());
        addNeighbours(changed, event.getBlock().getLocation());
        markDirty(changed);
        resyncAround(changed, 32.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshSmallArea(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshSmallArea(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        refreshSmallArea(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastMoveSyncAt.remove(playerId);
        lastFallbackSyncAt.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupState(now);

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (hasDirtyChunkNear(to, now)) {
            Long lastAt = lastMoveSyncAt.get(playerId);
            if (lastAt != null && now - lastAt < MOVE_SYNC_COOLDOWN_MS) {
                return;
            }

            lastMoveSyncAt.put(playerId, now);
            syncer.syncCubeForPlayer(player, 1, 1, 2);
            return;
        }

        Long lastFallbackAt = lastFallbackSyncAt.get(playerId);
        if (lastFallbackAt != null && now - lastFallbackAt < FALLBACK_SYNC_COOLDOWN_MS) {
            return;
        }

        lastFallbackSyncAt.put(playerId, now);
        syncer.syncCubeForPlayer(player, 0, 1, 2);
    }

    private void refreshSmallArea(Player player) {
        syncer.runNextTick(() -> syncer.syncCubeForPlayer(player, 4, 2, 4));
    }

    private void resyncAround(Collection<Location> changed, double distance) {
        syncer.runNextTick(() -> syncer.syncPlayersNear(changed, distance));
    }

    private void addNeighbours(Set<Location> target, Location center) {
        if (center.getWorld() == null) {
            return;
        }

        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        target.add(new Location(center.getWorld(), x + 1, y, z));
        target.add(new Location(center.getWorld(), x - 1, y, z));
        target.add(new Location(center.getWorld(), x, y + 1, z));
        target.add(new Location(center.getWorld(), x, y - 1, z));
        target.add(new Location(center.getWorld(), x, y, z + 1));
        target.add(new Location(center.getWorld(), x, y, z - 1));
    }

    private void markDirty(Collection<Location> changed) {
        long now = System.currentTimeMillis();
        long dirtyUntil = now + DIRTY_CHUNK_TTL_MS;
        cleanupState(now);

        for (Location location : changed) {
            if (location.getWorld() == null) {
                continue;
            }

            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            String worldId = location.getWorld().getUID().toString();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String key = worldId + ":" + (chunkX + dx) + ":" + (chunkZ + dz);
                    Long previous = dirtyChunks.get(key);
                    if (previous == null || previous < dirtyUntil) {
                        dirtyChunks.put(key, dirtyUntil);
                    }
                }
            }
        }
    }

    private boolean hasDirtyChunkNear(Location location, long now) {
        if (location.getWorld() == null) {
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        String worldId = location.getWorld().getUID().toString();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = worldId + ":" + (chunkX + dx) + ":" + (chunkZ + dz);
                Long dirtyUntil = dirtyChunks.get(key);
                if (dirtyUntil != null && dirtyUntil >= now) {
                    return true;
                }
            }
        }

        return false;
    }

    private void cleanupState(long now) {
        if (now < nextCleanupAt) {
            return;
        }

        nextCleanupAt = now + CLEANUP_INTERVAL_MS;
        dirtyChunks.entrySet().removeIf(entry -> entry.getValue() < now);
        lastMoveSyncAt.entrySet().removeIf(entry -> now - entry.getValue() > PLAYER_STATE_TTL_MS);
        lastFallbackSyncAt.entrySet().removeIf(entry -> now - entry.getValue() > PLAYER_STATE_TTL_MS);
    }
}
