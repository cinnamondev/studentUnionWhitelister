package com.github.cinnamondev.studentUnionWhitelister;

public class DiscordUsernameValidator {
    public static boolean isValidUsername(String username) {
        return username.matches("^[a-zA-Z0-9_.]+");
    }
}
