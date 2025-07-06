package com.github.cinnamondev.studentUnionWhitelister.dialog;

public class WhitelistRequest {
    public sealed interface Identifier { // this is the neatest little thing ive heard of for java... so nice :)
        record Email(String email) implements Identifier {}
        record StudentID(int studentID) implements Identifier {}
    }
}
