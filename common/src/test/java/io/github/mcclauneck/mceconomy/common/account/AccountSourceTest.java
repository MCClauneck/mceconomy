package io.github.mcclauneck.mceconomy.common.account;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.api.host.IdentitySource;
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two account bindings: player accounts without MCIdentity, identity
 * accounts with it.
 */
class AccountSourceTest {

    @Test
    void playersKeyWalletsByThePlayerUuid() {
        UUID player = UUID.randomUUID();
        AccountSource source = AccountSource.players();

        assertEquals(AccountTypes.PLAYER, source.accountType());
        assertTrue(source.isReady());
        assertEquals(Optional.of(player), source.accountUuid(player).join());
    }

    @Test
    void identitiesKeyWalletsByTheActiveIdentity() {
        UUID identity = UUID.randomUUID();
        AccountSource source = AccountSource.identities(identitySource(identity, true));

        assertEquals(AccountTypes.IDENTITY, source.accountType());
        assertTrue(source.isReady());
        assertEquals(Optional.of(identity), source.accountUuid(UUID.randomUUID()).join());
    }

    @Test
    void identitiesFollowTheHostsReadiness() {
        assertFalse(AccountSource.identities(identitySource(null, false)).isReady());
    }

    @Test
    void identitiesWithoutAnEquippedIdentityResolveEmpty() {
        AccountSource source = AccountSource.identities(identitySource(null, true));

        assertEquals(Optional.empty(), source.accountUuid(UUID.randomUUID()).join());
    }

    @Test
    void identitiesRequireABinding() {
        assertThrows(NullPointerException.class, () -> AccountSource.identities(null));
    }

    private static IdentitySource identitySource(UUID identity, boolean available) {
        return new IdentitySource() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public CompletableFuture<Optional<UUID>> activeIdentity(UUID playerUuid) {
                return CompletableFuture.completedFuture(Optional.ofNullable(identity));
            }
        };
    }
}
