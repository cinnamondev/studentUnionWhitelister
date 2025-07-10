package com.github.cinnamondev.studentUnionWhitelister;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentUnionWhitelister.dialog.WhitelistForm;
import com.github.cinnamondev.studentUnionWhitelister.discord.WhitelistRequest;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class WhitelistWatcher implements Listener {
    private SUWhitelister p;
    public WhitelistWatcher(SUWhitelister p) { this.p = p;}

    protected HashSet<PlayerProfile> awaitingConfiguration = new HashSet<>();
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUnwhitelistedPlayer(ProfileWhitelistVerifyEvent e) {
        if (!e.isWhitelisted()) {
            awaitingConfiguration.add(e.getPlayerProfile());
            e.setWhitelisted(true);
        }
        // TODO:
        // spam protection (check how long ago a connection last attempted whitelist process, if excessive or idle we will progressively increase the fail time...)
    }
    @EventHandler
    public void onPlayerConfigurationFinish(AsyncPlayerConnectionConfigureEvent e) {
        if (awaitingConfiguration.contains(e.getConnection().getProfile())) {
            awaitingConfiguration.remove(e.getConnection().getProfile());
            // hold up! we want to catch this guy!
            int restarts = 0;
            while (restarts < 10) {
                boolean isFinalAttempt = restarts == 9;
                var result = p.whitelistFormProvider.showWhitelistPrompt(e.getConnection().getProfile(), e.getConnection().getAudience(), true)
                        .completeOnTimeout( // if user hasnt completed form in 10 minutes, we boot em out.
                                new WhitelistForm.FormResult.Failure(Component.text("Timed out!"), false),
                                10, TimeUnit.MINUTES
                        )
                        .exceptionally(ex -> new WhitelistForm.FormResult.Failure(
                                Component.text("An error message was associated with this attempt:")
                                        .appendNewline()
                                        .append(Component.text(ex.getMessage())),
                                true
                        ))
                        .join(); // we will wait in this function for the result

                switch (result) {
                    case WhitelistForm.FormResult.Failure(Component message, boolean allowRetry) -> {
                        // display error message that could lead back into a TryAgain
                        if (isFinalAttempt) {
                            e.getConnection().disconnect(
                                    Component.text("You have ran out of attempts for this session. You can try again in `{}` minutes.")
                                            .appendNewline()
                                            .append(message)
                            );
                            return;
                        } else if (allowRetry) {
                            boolean retry = WhitelistForm.errorDialog(message, e.getConnection().getProfile(), e.getConnection().getAudience())
                                    .exceptionally(ex -> {
                                        p.getLogger().warning(ex.getLocalizedMessage());
                                        return false;
                                    }).join();

                            if (!retry) {
                                e.getConnection().disconnect(WhitelistForm.DISCONNECT_REASON_CANCELLED.reason());
                                return;
                            } else {
                                restarts += 1;
                            }
                        } else { // generic disconnect with message
                            e.getConnection().disconnect(message);
                        }
                    }
                    case WhitelistForm.FormResult.Complete(WhitelistRequest request) -> {
                        try {
                            p.acceptor.acceptWhitelistRequest(request);
                            e.getConnection().disconnect(Component.text("Thanks!"));
                        } catch (Exception ex) {
                            if (isFinalAttempt) {
                                e.getConnection().disconnect(
                                        Component.text("An error message was associated with this attempt:")
                                                .appendNewline()
                                                .append(Component.text(ex.getMessage()))
                                );
                            } else {
                                boolean retry = WhitelistForm.errorDialog(
                                        Component.text("An error message was associated with this attempt:")
                                                .appendNewline()
                                                .append(Component.text(ex.getMessage())),
                                        e.getConnection().getProfile(), e.getConnection().getAudience()
                                ).join();
                                if (!retry) {
                                    e.getConnection().disconnect(WhitelistForm.DISCONNECT_REASON_CANCELLED.reason());
                                    return;
                                } else {
                                    restarts += 1;
                                }
                            }
                        }
                        return;
                    }
                }
            }
            // 'just in case' we forgot to finish early somewhere, disconnect the player with a generic message.
            e.getConnection().disconnect(WhitelistForm.DISCONNECT_REASON_CANCELLED.reason());
        }
    }
}
