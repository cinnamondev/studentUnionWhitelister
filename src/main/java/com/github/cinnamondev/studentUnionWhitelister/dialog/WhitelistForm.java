package com.github.cinnamondev.studentUnionWhitelister.dialog;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.cinnamondev.studentUnionWhitelister.DiscordUsernameValidator;
import com.github.cinnamondev.studentUnionWhitelister.SUWhitelister;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.geysermc.floodgate.api.FloodgateApi;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WhitelistForm {
    public sealed interface FormResult {
        record Complete(WhitelistRequest request) implements FormResult {}
        record Failure(Component reason, boolean allowRetry) implements FormResult {}
    }

    public static Style HYPERLINK_STYLE = Style.style(NamedTextColor.AQUA, TextDecoration.UNDERLINED);

    protected static Component STUDENT_UNION_LINK = Component.text("Click Here")
            .style(HYPERLINK_STYLE)
            .clickEvent(ClickEvent.openUrl("https://manchesterstudentsunion.com/activities/view/minecraft"));

    protected static Component DISCORD_LINK = Component.text("Discord Server")
            .style(HYPERLINK_STYLE)
            .clickEvent(ClickEvent.openUrl("https://manchesterstudentsunion.com/activities/view/minecraft"));

    protected static Component DIALOG_MESSAGE = Component.text(
                    "It seems you aren't whitelisted yet! In order to get whitelisted, you must sign up as a member " +
                            "with the Student Union (").append(STUDENT_UNION_LINK).append(Component.text(")"))
            .append(Component.text("(Also join our ")).append(DISCORD_LINK).append(Component.text(")"))
            .appendNewline()
            .append(Component.text(
                    "Once you've done so, enter your student email/id below, as well as Discord " +
                            "username, and a committee member will review your request :)"
            ));

    public static FormResult.Failure DISCONNECT_REASON_CANCELLED = new FormResult.Failure(
            Component.empty() // styling
                    .append(STUDENT_UNION_LINK)
                    .append(Component.text(" to be taken to our Student Union page. If you are experiencing issues, you can get help via our "))
                    .append(DISCORD_LINK)
                    .appendNewline().append(Component.text("We hope to see you soon!")),
            false
    );

    protected static TextDialogInput IDENTIFIER_INPUT = DialogInput.text(
            "identifier",
            Component.join(JoinConfiguration.separator(Component.text(" / ")),
                    Component.text("E-Mail")
                            .hoverEvent(HoverEvent.showText(Component.text("i.e.: first.last@student.manchester.ac.uk"))),
                    Component.text("Student ID")
                            .hoverEvent(HoverEvent.showText(Component.text("i.e.: 11095134")))
            )
    ).labelVisible(true).maxLength(256).build();

    protected static TextDialogInput DISCORD_INPUT = DialogInput.text(
            "discord",
            Component.text("Discord Username")
                    .hoverEvent(HoverEvent.showText(Component.text("i.e. jpromptig")))
    ).labelVisible(true).maxLength(64).build();

    protected static SingleOptionDialogInput IS_JAVA_OR_BEDROCK = DialogInput.singleOption(
            "platform",
            Component.text("Platform: "),
            List.of(
                    SingleOptionDialogInput.OptionEntry.create("java", Component.text("I play on Java Edition!"), true),
                    SingleOptionDialogInput.OptionEntry.create("java", Component.text("I play on Bedrock Edition!"), false)
            )).build();




    protected final List<DialogInput> dialogInputs;
    protected final SUWhitelister p;
    public WhitelistForm(SUWhitelister plugin) {
        this.p = plugin;
        if (plugin.floodgateInstance().isPresent()) {
            this.dialogInputs = List.of(
                    IDENTIFIER_INPUT,
                    DISCORD_INPUT
            );
        } else {
            this.dialogInputs = List.of(
                    IDENTIFIER_INPUT,
                    DISCORD_INPUT,
                    IS_JAVA_OR_BEDROCK
            );
        }
    }

    protected static ActionButton genericCancelButton(Consumer<FormResult.Failure> failureConsumer) {
        return ActionButton.builder(Component.text("Cancel").color(NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.callback(_a -> {
                    failureConsumer.accept(DISCONNECT_REASON_CANCELLED);
                })))
                .build();
    }

    public CompletableFuture<FormResult> showWhitelistPrompt(PlayerProfile profile, Audience audience, boolean allowRetry) {
        CompletableFuture<FormResult> future = new CompletableFuture<>(); // seeing as players could be coming back at any time we return a completablefuture
        AtomicBoolean tryAgain = new AtomicBoolean(true);
        Dialog dialog = Dialog.create(b -> b.empty()
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("I am registered!").color(NamedTextColor.GREEN))
                                .action(DialogAction.customClick((r, _a) -> {
                                    boolean success = false;

                                    WhitelistRequest.Identifier identifier;
                                    {
                                        String unvalidatedIdentifier = r.getText("identifier");
                                        FormResult.Failure failureMessage = new FormResult.Failure(Component.text(
                                                "Could not parse student ID / email!"
                                        ), allowRetry);
                                        if (unvalidatedIdentifier == null) {
                                            future.complete(failureMessage);
                                            return;
                                        }
                                        unvalidatedIdentifier = unvalidatedIdentifier.strip().trim();
                                        // lets see if we can parse for id
                                        if (NumberUtils.isCreatable(unvalidatedIdentifier)) {
                                            int num = NumberUtils.toInt(unvalidatedIdentifier);
                                            if (num == 0) { future.complete(failureMessage); return; } else {
                                                identifier = new WhitelistRequest.Identifier.StudentID(num);
                                            }
                                        } else if (EmailValidator
                                                .getInstance(false, false)
                                                .isValid(unvalidatedIdentifier)) {
                                            identifier = new WhitelistRequest.Identifier.Email(unvalidatedIdentifier);
                                        } else { // not email or id :(
                                            future.complete(failureMessage);
                                            return;
                                        }
                                    }

                                    String discordUsername;
                                    {
                                        String unvalidatedDiscord = r.getText("discord");
                                        if (unvalidatedDiscord == null) {
                                            future.complete(new FormResult.Failure(Component.text("missing discord username"), allowRetry));
                                            return;
                                        }
                                        unvalidatedDiscord = unvalidatedDiscord.strip().trim();
                                        // discord usernames only usually have periods and underscores. they also
                                        // dont allow `..` etc but idrc about that at this point, just want to make
                                        // sure its 'pretty' clean.
                                        if (!unvalidatedDiscord.matches("^[a-zA-Z0-9_.]+")) {
                                            future.complete(new FormResult.Failure(Component.text("Invalid Discord Username!"), allowRetry));
                                            return;
                                        } else {
                                            discordUsername = unvalidatedDiscord;
                                        }
                                    }

                                    boolean isJava;
                                    if (p.floodgateInstance().isPresent()) {
                                        FloodgateApi api = p.floodgateInstance().get();
                                        if (p.floodgateInstance().get().isFloodgatePlayer(profile.getId())) {
                                            isJava = false;
                                        }
                                    } else {
                                        isJava = Boolean.TRUE.equals(r.getBoolean("java"));
                                    }

                                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.of(15, ChronoUnit.MINUTES)).build()))
                                .build(),
                        genericCancelButton(future::complete)
                ))
                .base(DialogBase.builder(Component.text("Get whitelisted!"))
                        .externalTitle(Component.text("Get whitelisted!"))
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(DIALOG_MESSAGE)
                        ))
                        .inputs(dialogInputs)
                        .canCloseWithEscape(false)
                        .build()
                )
        );

        audience.showDialog(dialog);
        return future;
    }

    public static CompletableFuture<Boolean> errorDialog(Component errorMessage, PlayerProfile profile, Audience audience) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Dialog dialog = Dialog.create(b -> b.empty()
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Try again"))
                                .action(DialogAction.staticAction(ClickEvent.callback(_a -> future.complete(true))))
                                .build(),
                        ActionButton.builder(Component.text("Cancel"))
                                .action(DialogAction.staticAction(ClickEvent.callback(_a -> future.complete(false))))
                                .build()
                ))
                .base(DialogBase.builder(Component.text("Couldn't complete request!")).body(List.of(
                        DialogBody.item(ItemStack.of(Material.BARRIER, 1))
                                .showTooltip(false)
                                .showDecorations(false)
                                .build(),
                        DialogBody.plainMessage(Component.text("An error occured! Please see below:")),
                        DialogBody.plainMessage(errorMessage))).build()
                )
        );

        audience.showDialog(dialog);
        return future;
    }

}