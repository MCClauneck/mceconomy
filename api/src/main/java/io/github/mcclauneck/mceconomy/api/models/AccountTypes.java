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
     * Accounts owned by an MCIdentity identity. The account UUID is the
     * <strong>identity UUID</strong> (never the base player UUID), so each
     * identity a player owns keeps its own separate wallets and logs.
     */
    public static final String IDENTITY = "identity";

    /**
     * Accounts owned by a player. The account UUID is the player UUID. Used
     * when MCIdentity is not installed, so each player keeps one set of wallets.
     */
    public static final String PLAYER = "player";

    private AccountTypes() {
    }
}
