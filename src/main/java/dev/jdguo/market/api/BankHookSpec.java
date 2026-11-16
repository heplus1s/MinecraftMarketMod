package dev.jdguo.market.api;

/**
 * Bank-side hook this market looks up when {@link BankSettlement} and
 * {@link LandGrantAccount} were not installed. The current bank mod does not
 * contain this class. Do not satisfy it by writing private ledger fields from
 * the outside: a savings transfer has to keep 储蓄总池, issuance reserves, and
 * the shop operating account on their existing rules.
 * <p>
 * Add this class in the bank mod:
 * <pre>
 * package dev.jdguo.bank.api;
 *
 * public final class MarketSettlement {
 *     public static TxResult transfer(UUID from, UUID to, String currency,
 *                                     BigInteger amount, String reference) { ... }
 *
 *     public static TxResult creditLandGrant(UUID payer, String currency,
 *                                             BigInteger amount, String reference) { ... }
 *
 *     public static TxResult refundLandGrant(UUID payer, String currency,
 *                                             BigInteger amount, String reference) { ... }
 * }
 * </pre>
 * {@code transfer} pays the seller. {@code creditLandGrant} pays the 土地出让账户,
 * which is an interest-bearing sub-account of 储蓄总池. {@code refundLandGrant}
 * reverses that credit back to the payer. The charge reference is
 * {@code sale:} plus the market listing id. The refund reference is
 * {@code refund:} plus the deal id. Return {@code insufficient_funds} when the
 * payer's savings cannot cover the whole amount, and do not take a partial payment.
 * Save the ledger before returning.
 * <p>
 * The bank's own 银行券 trading market stays in the bank mod. This market does
 * not list notes and does not call that market.
 */
public final class BankHookSpec {
    public static final String BANK_CLASS = "dev.jdguo.bank.api.MarketSettlement";
    public static final String TRANSFER = "transfer";
    public static final String CREDIT_LAND_GRANT = "creditLandGrant";
    public static final String REFUND_LAND_GRANT = "refundLandGrant";

    private BankHookSpec() {
    }
}
