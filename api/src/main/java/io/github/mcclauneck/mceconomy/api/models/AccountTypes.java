package io.github.mcclauneck.mceconomy.api.models;

/**
 * Well-known {@code accountType} values used to identify an {@link Account}.
 * <p>
 * An account is keyed by an account type plus a UUID, and the type string is
 * free-form. The constants here name the types with agreed-upon semantics so
 * every module and third-party consumer spells them identically.
 */
public final class AccountTypes {

    /**
     * Accounts owned by a player. The account UUID is the player UUID, so each
     * player keeps one set of wallets whichever MCIdentity profession they wear.
     */
    public static final String PLAYER = "player";

    private AccountTypes() {
    }
}
