package org.teighto.ghostBlockFix;

import org.bukkit.plugin.java.JavaPlugin;

public final class main extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Syncer syncer = new Syncer(this);
        getServer().getPluginManager().registerEvents(new Events(this, syncer), this);

        String configuredVersion = getConfig().getString("plugin-version", getPluginMeta().getVersion());
        String configuredAuthor = getConfig().getString("author", "unknown");
        getLogger().info("GhostBlockFix " + configuredVersion + " by " + configuredAuthor + " is active.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GhostBlockFix stopped.");
    }
}
