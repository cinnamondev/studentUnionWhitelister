package com.github.cinnamondev.studentUnionWhitelister.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import reactor.core.Disposable;

import java.util.List;

public class WhitelistCommand {
    public static Disposable suggestionListener(GatewayDiscordClient g) {
        return g.on(ChatInputAutoCompleteEvent.class).subscribe(e -> {
            if (e.getCommandName().equals("whitelist")) { // only suggest 2 options so we wont bother fuzzying or something
                e.respondWithSuggestions(List.of(
                        ApplicationCommandOptionChoiceData.builder().name("Java").value("java").build(),
                        ApplicationCommandOptionChoiceData.builder().name("Bedrock").value("bedrock").build()
                )).subscribe();
            }
        });
    }

    public static ImmutableApplicationCommandRequest command() {
        return ApplicationCommandRequest.builder()
                .name("whitelist")
                .description("whitelist a player")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("student id/email")
                        .description("student id (i.e. 11095134) or email (i.e: first.last@student.manchester.ac.uk)")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .maxLength(16)
                        .required(true)
                        .build()
                ).addOption(ApplicationCommandOptionData.builder()
                        .name("username")
                        .description("your normal java or bedrock username (i.e. MikeOnABike)")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .maxLength(16)
                        .required(true)
                        .build()
                ).addOption(ApplicationCommandOptionData.builder()
                        .name("platform")
                        .description("do you play Java Edition or Bedrock Edition?")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build()
                ).build();
    }

}
