package com.github.cinnamondev.studentUnionWhitelister;

import com.github.cinnamondev.studentUnionWhitelister.dialog.WhitelistForm;
import com.github.cinnamondev.studentUnionWhitelister.discord.DiscordBot;
import com.github.cinnamondev.studentUnionWhitelister.discord.WhitelistRequest;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public final class SUWhitelister extends JavaPlugin {
    public DiscordBot bot = null;
    private WhitelistWatcher watcher;

    public Optional<FloodgateApi> floodgateInstance() {
        if (getServer().getPluginManager().isPluginEnabled("Floodgate")) {
            return Optional.of(FloodgateApi.getInstance());
        } else {
            return Optional.empty();
        }
    }
    public Optional<DiscordBot> bot() { return Optional.ofNullable(bot); }

    @Override
    public void onEnable() {
        if (getConfig().getBoolean("discord.enabled", false)) {
            // get the discord bot started up in an async thread, it should give us a shout when its ready
            DiscordBot.start(this).subscribe(bot -> {
                this.bot = bot;
            }, ex -> {
                getLogger().severe(ex.getMessage());
                getLogger().severe("discord bot will be disabled");
            });
        } else {
            // register outgoing plugin channel so we can send off our whitelist data to someone else
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord"); // forwarding data
        }

        this.watcher = new WhitelistWatcher(this);
        getServer().getPluginManager().registerEvents(watcher, this);
        // Plugin startup logic

    }

    public CompletableFuture<Void> sendRequestToBestSource(WhitelistRequest r) {
        if (bot != null) {
            return bot.submitWhitelistRequest(r);
        } else {
            getLogger().warning("not implemented feature");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
