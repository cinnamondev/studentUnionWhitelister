package com.github.cinnamondev.studentUnionWhitelister;

import com.github.cinnamondev.studentUnionWhitelister.dialog.WhitelistRequest;

import java.util.concurrent.CompletableFuture;

public class MockWhitelistAcceptor {
    private SUWhitelister p;
    public MockWhitelistAcceptor(SUWhitelister plugin) { this.p = plugin; }
    public CompletableFuture<Void> acceptWhitelistRequest(WhitelistRequest whitelistRequest) {
        return CompletableFuture.runAsync(() -> {
            try {
                wait(2000);
                p.getLogger().warning("Hello world");

                p.getLogger().warning(whitelistRequest.toString());
                //throw new RuntimeException("Hello World");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
