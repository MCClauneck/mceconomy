package io.github.mcclauneck.mceconomy.common.account;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account binding: every wallet belongs to the player, keyed by the player
 * UUID under account type {@code player}.
 */
class AccountSourceTest {

    @Test
    void playersKeyWalletsByThePlayerUuid() {
        UUID player = UUID.randomUUID();
        AccountSource source = AccountSource.players();

        assertEquals(AccountTypes.PLAYER, source.accountType());
        assertEquals("player", source.accountType());
        assertTrue(source.isReady());
        assertEquals(Optional.of(player), source.accountUuid(player).join());
    }
}
