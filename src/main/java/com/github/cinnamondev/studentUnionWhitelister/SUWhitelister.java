package com.github.cinnamondev.studentUnionWhitelister;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentUnionWhitelister.dialog.WhitelistForm;
import com.github.cinnamondev.studentUnionWhitelister.dialog.WhitelistRequest;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SUWhitelister extends JavaPlugin {
    public MockWhitelistAcceptor acceptor;
    public WhitelistForm whitelistFormProvider;
    private WhitelistWatcher watcher;

    public Optional<FloodgateApi> floodgateInstance() {
        if (getServer().getPluginManager().isPluginEnabled("Floodgate")) {
            return Optional.of(FloodgateApi.getInstance());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void onEnable() {
        this.acceptor = new MockWhitelistAcceptor(this);
        this.whitelistFormProvider = new WhitelistForm(this);
        this.watcher = new WhitelistWatcher(this);
        getServer().getPluginManager().registerEvents(watcher, this);
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
