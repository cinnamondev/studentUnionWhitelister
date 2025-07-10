package com.github.cinnamondev.studentUnionWhitelister.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.interaction.GuildCommandRegistrar;
import io.netty.handler.codec.http.HttpResponseStatus;
import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.bukkit.plugin.Plugin;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class DiscordBot {
    // L: whitelistrequest R: future to determine outcome of (likely complete gracefully)
    protected Sinks.Many<Pair<WhitelistRequest, CompletableFuture<Void>>> sink = Sinks.many().unicast().onBackpressureBuffer();
    protected final Plugin p;
    protected final GatewayDiscordClient g;
    protected final Guild guild;
    protected final TextChannel requestChannel;
    private DiscordBot(Plugin p, GatewayDiscordClient g, Guild guild, TextChannel requestChannel) {
        this.p = p;
        this.g = g;
        this.guild = guild;
        this.requestChannel = requestChannel;
        sink.asFlux().subscribe(this::processWhitelistRequest);
    }

    public static Mono<DiscordBot> start(Plugin p) {
        return DiscordClient.create(Objects.requireNonNull(p.getConfig().getString("discord.secret")))
                .login()
                .flatMap(g ->
                    // this *Must* happen -- we need to get the first guilds stuff out before giving out out the bot.
                    g.on(GuildCreateEvent.class, e ->
                            // 1. register commands
                            GuildCommandRegistrar.create(g.getRestClient(), Collections.singletonList(WhitelistCommand.command()))
                                    .registerCommands(e.getGuild().getId())
                                    .doOnError(ex -> p.getLogger().warning("Unable to register whitelist guild command " + e))
                                    .onErrorResume(ex -> Mono.empty())
                                    .then(Mono.zip( // 2. extract the guild and whitelist channel data we need for stuff.
                                            Mono.just(g),
                                            Mono.just(e.getGuild()),
                                            g.getChannelById(
                                                    Snowflake.of(p.getConfig().getString("discord.whitelist-channel", ""))
                                            ).ofType(TextChannel.class)
                                    ))
                    ).next())
                .map(t -> new DiscordBot(p, t.getT1(), t.getT2(), t.getT3()))
                .retry(2)
                .timeout(Duration.ofMinutes(2));
    }

    protected MessageCreateSpec whitelistRequestMessage(WhitelistRequest request) {
        return MessageCreateSpec.builder()
                .content(new StringJoiner("\n")
                        .add(request.id().label() + ": " + request.id().identifier())
                        .add("Username: " + request.username().entry())
                        .add("Discord: " + request.discord().getMention())
                        .toString()
                )
                .addAllComponents(List.of(
                        ActionRow.of(
                                Button.success("acceptWhitelistRequest", "Accept"),
                                SelectMenu.of("rejectWithReason",
                                        SelectMenu.Option.of("Not registered with Student Union", "not_registered"),
                                        SelectMenu.Option.of("Invalid email address", "invalid_email"),
                                        SelectMenu.Option.of("Invalid student ID", "invalid_id"),
                                        SelectMenu.Option.of("Unknown (ask in #help)", "unknown_error")
                                )
                        )
                ))
                .build();
    }
    protected void processWhitelistRequest(Pair<WhitelistRequest, CompletableFuture<Void>> request) {
        requestChannel.createMessage(whitelistRequestMessage(request.left()))
                .subscribe(_m -> request.right().complete(null), ex -> request.right().completeExceptionally(ex));
    }

    /**
     * eagerly send a whitelist request through the sink, failing the returned future exceptionally if it doesnt work.
     * future may then later respond with a completion value once the sink has been handled.
     * @param whitelistRequest whitelist request
     * @return future representing whitelist status
     */
    public CompletableFuture<Void> submitWhitelistRequest(WhitelistRequest whitelistRequest) {
        var future = new CompletableFuture<Void>();
        var result = sink.tryEmitNext(Pair.of(whitelistRequest, future));
        try {
            result.orThrow();
        } catch (Exception e) { // immediately fail if the kitchen sinks broke
            future.completeExceptionally(e);
        }
        return future;
    }
}
