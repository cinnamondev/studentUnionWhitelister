package com.github.cinnamondev.studentUnionWhitelister.discord;

import com.github.cinnamondev.studentUnionWhitelister.SUWhitelister;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.UserData;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record WhitelistRequest(Identifier id, Username username, User discord) {
    public sealed interface Username {
        record Java(@NotNull String username, UUID uuid) implements Username {
            @Override public String platform() { return "Java"; }
        }
        record Bedrock(@NotNull String username, UUID floodgateUUID) implements Username {
            @Override public String platform() { return "Bedrock"; }
        }
        String username();
        String platform();
        default String entry() {
            return username() + " (" + platform() +")";
        }
        /**
         * create and validate a username for the specified platorm
         * @param p java plugin (used to access server for java profile validation)
         * @param username username to parse
         * @param isJava platform
         * @apiNote if you know you are guaranteed to pass a java or bedrock username (i.e if you are pulling data from
         * the game, you should *probably* just directly instantiate Username.
         * @implNote this is going to be a blocking action (as is implied)
         * @return validated username
         */
        static Mono<WhitelistRequest.Username> tryFromString(SUWhitelister p, String username, boolean isJava) {
            if (isJava) {
                return Mono.fromSupplier(() -> {
                    var profile = p.getServer().createProfile(username);
                    if (profile.complete(false)) { // no textures plz!
                        return new Java(Objects.requireNonNull(profile.getName()), Objects.requireNonNull(profile.getId()));
                    } else {
                        throw new IllegalArgumentException("Not a valid Java profile");
                    }
                });
            } else {
                return p.floodgateInstance().map(api -> Mono.fromFuture(api.getUuidFor(username))
                        .map(uuid -> (WhitelistRequest.Username) new Bedrock(username, uuid))
                ).orElse(Mono.error(new IllegalArgumentException("cant check bedrock profile without floodgate")));
            }
        }
    }

    public sealed interface Identifier {
        record Email(String email) implements Identifier {
            @Override public String label() {return "E-Mail"; }
            @Override public String identifier() { return email; }
        }
        record Id(int id) implements Identifier {
            @Override public String label() {return "Student ID"; }
            @Override public String identifier() { return String.valueOf(id); }
        }
        String label();
        String identifier();
        /**
         * Create an Identifier best fit for the input data, throwing an exception if it is not parseable.
         * @param string string to parse
         * @return identifier
         */
        static Identifier tryFromString(String string) {
            String str = string.trim();
            // no local uri, no foo@gov (only tld)
            EmailValidator validator = EmailValidator.getInstance(false, false);
            // first determine numeric
            if (NumberUtils.isNumber(str)) {
                return new Id(NumberUtils.toInt(str));
            } else if (validator.isValid(str)) {
                return new Email(str);
            } else {
                throw new IllegalArgumentException("Invalid email / numeric ID: " + string);
            }
        }
    }

    private static Mono<User> tryGetDiscordUser(Guild g, String username) {
        if (!username.matches("^[a-zA-Z0-9_.]+")) { return Mono.error(new IllegalArgumentException("Invalid username")); }

        return g.getMembers()
                .filter(m -> !m.isBot())
                .filter(m -> m.getUsername().equals(username))
                .next().map(m -> m); // does java just not know what types are ?? why do i need to do this
    }

    /**
     * create a whitelist request validating the information that would come from discord
     * @param p plugin
     * @param unparsedIdentifier unparsed student email/id
     * @param username unparsed minecraft username
     * @param discord discord user (well-formed)
     * @param java platform
     * @apiNote if you already have a well-formed minecraft user but not a discord user, refer to tryGetMinecraftUser
     * @return fully formed whitelist request (or return error)
     */
    public static Mono<WhitelistRequest> tryWithDiscord(SUWhitelister p, String unparsedIdentifier, String username, User discord, boolean java) {
        return Mono.zip(
                Mono.fromSupplier(() -> Identifier.tryFromString(unparsedIdentifier)),
                Username.tryFromString(p, username, java)
        ).map(t -> new WhitelistRequest(t.getT1(), t.getT2(), discord));
    }

    /**
     * create a whitelist request validating the information that would come from minecraft
     * @param bot discord bot
     * @param unparsedIdentifier unparsed student email/id
     * @param minecraft well-formed minecraft username
     * @param discord unformed discord user
     * @apiNote if you already have a well-formed discord user but not a minecraft user, refer to tryGetDiscordUser
     * @return fully formed whitelist request (or return error)
     */
    public static Mono<WhitelistRequest> tryWithMinecraft(DiscordBot bot, String unparsedIdentifier, Username minecraft, String discord) {
        return Mono.zip(
                Mono.fromSupplier(() -> Identifier.tryFromString(unparsedIdentifier)),
                WhitelistRequest.tryGetDiscordUser(bot.guild, discord)
        ).map(t -> new WhitelistRequest(t.getT1(), minecraft, t.getT2()));
    }
}
