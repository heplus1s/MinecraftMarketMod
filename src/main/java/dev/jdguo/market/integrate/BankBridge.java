package dev.jdguo.market.integrate;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.api.BankHookSpec;
import dev.jdguo.market.api.BankSettlement;
import dev.jdguo.market.api.LandGrantAccount;
import dev.jdguo.market.api.SettlementResult;
import dev.jdguo.market.listing.Payments;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/**
 * Uses an installed settlement hook, or the bank class named by {@link BankHookSpec}.
 * It does not touch private ledger fields.
 */
public final class BankBridge {
    private static final Payments PAYMENTS = new Payments() {
        @Override
        public boolean requiresFinalization(String currency) {
            if (currency.startsWith("personal:")) return true;
            try { Class.forName(BankHookSpec.BANK_CLASS).getMethod("quoteLandPurchase", String.class, BigInteger.class, String.class); return true; }
            catch (ReflectiveOperationException missing) { return false; }
        }
        @Override
        public SettlementResult finalizePayment(String reference) {
            // Custom installed hooks predate clearing locks and retain their no-op contract.
            if (LandGrantAccount.installed() != null) return SettlementResult.ok();
            return reflect("completePayment", new Class<?>[]{String.class}, new Object[]{reference}, "finalize_unavailable");
        }

        @Override
        public SettlementResult receipt(String reference) {
            if (BankSettlement.installed() != null || LandGrantAccount.installed() != null) {
                return SettlementResult.fail("receipt_unavailable", "");
            }
            return reflect("marketReceipt", new Class<?>[]{String.class}, new Object[]{reference}, "receipt_unavailable");
        }

        @Override
        public SettlementResult payGovernmentResale(UUID buyer, String currency, BigInteger amount, String reference) {
            if (!"note".equals(currency)) return SettlementResult.fail("unknown_currency", "");
            var quote = landQuote("note", amount, "note");
            if (!governmentResaleQuoteMatches(quote, amount)) return SettlementResult.fail("government_resale_missing", "");
            // Check the strict recovery API before debiting, even if general land quote APIs exist.
            if (!governmentResaleAvailable()) return SettlementResult.fail("government_resale_missing", "");
            return reflect("creditLandGrantQuoted", new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class, String.class, String.class},
                    new Object[]{buyer, "note", amount, "note", quote.get("fingerprint").getAsString(), reference}, "government_resale_missing");
        }

        @Override
        public SettlementResult refundGovernmentResale(UUID buyer, String currency, BigInteger amount, String reference) {
            return reflect(BankHookSpec.REFUND_LAND_GRANT, new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{buyer, currency, amount, reference}, "government_resale_missing");
        }

        @Override
        public SettlementResult finalizeGovernmentResale(String reference) {
            return reflect("completeLandPurchase", new Class<?>[]{String.class}, new Object[]{reference}, "finalize_unavailable");
        }

        @Override
        public SettlementResult governmentReceipt(String reference) {
            return reflect("marketReceipt", new Class<?>[]{String.class}, new Object[]{reference}, "receipt_unavailable");
        }

        @Override
        public SettlementResult finalizeGovernmentBuyback(String reference) {
            return reflect("completeLandBuyback", new Class<?>[]{String.class}, new Object[]{reference}, "finalize_unavailable");
        }

        @Override
        public SettlementResult prepareGovernmentBuyback(UUID seller, String currency, BigInteger amount, String reference) {
            return reflect("prepareLandBuyback", new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{seller, currency, amount, reference}, "government_buyback_missing");
        }

        @Override
        public SettlementResult refundGovernmentBuyback(UUID seller, String currency, BigInteger amount,
                                                        String paymentReference, String refundReference) {
            return reflect("refundLandBuyback", new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class, String.class},
                    new Object[]{seller, currency, amount, paymentReference, refundReference}, "government_buyback_missing");
        }

        @Override
        public SettlementResult paySeller(UUID buyer, UUID seller, String currency, BigInteger amount, String reference) {
            BankSettlement installed = BankSettlement.installed();
            if (installed != null) {
                return installed.transfer(buyer, seller, currency, amount, reference);
            }
            return reflect(BankHookSpec.TRANSFER,
                    new Class<?>[]{UUID.class, UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{buyer, seller, currency, amount, reference}, "bank_hook_missing");
        }

        @Override
        public SettlementResult payLandGrant(UUID buyer, String currency, BigInteger amount, String reference) {
            LandGrantAccount account = LandGrantAccount.installed();
            if (account != null) {
                return account.receive(buyer, currency, amount, reference);
            }
            return reflect(BankHookSpec.CREDIT_LAND_GRANT,
                    new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{buyer, currency, amount, reference}, "land_grant_missing");
        }

        @Override
        public SettlementResult refundSeller(UUID seller, UUID buyer, String currency, BigInteger amount, String reference) {
            BankSettlement installed = BankSettlement.installed();
            if (installed != null) {
                return installed.transfer(seller, buyer, currency, amount, reference);
            }
            return reflect(BankHookSpec.TRANSFER,
                    new Class<?>[]{UUID.class, UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{seller, buyer, currency, amount, reference}, "bank_hook_missing");
        }

        @Override
        public SettlementResult refundLandGrant(UUID buyer, String currency, BigInteger amount, String reference) {
            LandGrantAccount account = LandGrantAccount.installed();
            if (account != null) {
                return account.refund(buyer, currency, amount, reference);
            }
            return reflect(BankHookSpec.REFUND_LAND_GRANT,
                    new Class<?>[]{UUID.class, String.class, BigInteger.class, String.class},
                    new Object[]{buyer, currency, amount, reference}, "land_grant_missing");
        }
    };

    private BankBridge() {
    }

    public static boolean governmentResaleAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("bankmod")) return false;
        try {
            Class<?> type = Class.forName(BankHookSpec.BANK_CLASS);
            type.getMethod("completeLandPurchase", String.class);
            type.getMethod("marketReceipt", String.class);
            type.getMethod(BankHookSpec.REFUND_LAND_GRANT, UUID.class, String.class, BigInteger.class, String.class);
            type.getMethod("creditLandGrantQuoted", UUID.class, String.class, BigInteger.class, String.class, String.class, String.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError missing) { return false; }
    }

    static boolean governmentResaleQuoteMatches(com.google.gson.JsonObject quote, BigInteger amount) {
        try {
            return quote.get("success").getAsBoolean()
                    && "note".equals(quote.get("paymentCurrency").getAsString())
                    && new BigDecimal(quote.get("quantity").getAsString()).compareTo(new BigDecimal(amount)) == 0
                    && new BigDecimal(quote.get("noteTopup").getAsString()).signum() == 0
                    && !quote.get("fingerprint").getAsString().isBlank();
        } catch (RuntimeException invalid) { return false; }
    }

    public static SettlementResult quoteGovernmentBuyback(UUID seller, String currency, BigInteger amount) {
        return reflect("quoteLandBuyback", new Class<?>[]{UUID.class, String.class, BigInteger.class},
                new Object[]{seller, currency, amount}, "government_buyback_missing");
    }

    public static Payments payments() {
        return PAYMENTS;
    }

    public static com.google.gson.JsonObject landQuote(String denomination, BigInteger total, String tender) {
        try {
            Class<?> type = Class.forName(BankHookSpec.BANK_CLASS);
            String json = (String) type.getMethod("quoteLandPurchase", String.class, BigInteger.class, String.class).invoke(null, denomination, total, tender);
            return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return new com.google.gson.JsonObject(); }
    }
    /** Only the initial-grant charge changes. Recovery/refund still uses the original durable bank receipt. */
    public static Payments landPayments(String tender, String fingerprint) {
        return new Payments() {
            public boolean requiresFinalization(String c) { return true; }
            public SettlementResult receipt(String r) { return PAYMENTS.receipt(r); }
            public SettlementResult finalizePayment(String r) { return PAYMENTS.finalizePayment(r); }
            public SettlementResult paySeller(UUID a, UUID b, String c, BigInteger n, String r) { return PAYMENTS.paySeller(a,b,c,n,r); }
            public SettlementResult payLandGrant(UUID a, String c, BigInteger n, String r) {
                return reflect("creditLandGrantQuoted", new Class<?>[]{UUID.class,String.class,BigInteger.class,String.class,String.class,String.class},
                        new Object[]{a,c,n,tender,fingerprint,r}, "land_quote_missing");
            }
            public SettlementResult refundSeller(UUID a, UUID b, String c, BigInteger n, String r) { return PAYMENTS.refundSeller(a,b,c,n,r); }
            public SettlementResult refundLandGrant(UUID a, String c, BigInteger n, String r) { return PAYMENTS.refundLandGrant(a,c,n,r); }
        };
    }

    public static SettlementResult personalOffers(UUID player, String name) {
        return reflect("personalOffers", new Class<?>[]{UUID.class, String.class},
                new Object[]{player, name}, "personal_market_missing");
    }

    public static SettlementResult personalAmount(String method, UUID player, String name, BigInteger amount) {
        return reflect(method, new Class<?>[]{UUID.class, String.class, BigInteger.class},
                new Object[]{player, name, amount}, "personal_market_missing");
    }

    public static SettlementResult buyPersonal(UUID player, String name, BigInteger amount, BigDecimal maximum) {
        return reflect("buyPersonal", new Class<?>[]{UUID.class, String.class, BigInteger.class, BigDecimal.class},
                new Object[]{player, name, amount, maximum}, "personal_market_missing");
    }

    private static SettlementResult reflect(String name, Class<?>[] types, Object[] args, String missingCode) {
        if (!FabricLoader.getInstance().isModLoaded("bankmod")) {
            return SettlementResult.fail("bank_missing", "");
        }
        try {
            Class<?> type = Class.forName(BankHookSpec.BANK_CLASS);
            Method method = type.getMethod(name, types);
            return adapt(method.invoke(null, args));
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return SettlementResult.fail(missingCode, name);
        } catch (ReflectiveOperationException exception) {
            MarketMod.LOGGER.error("Bank hook {} failed", name, exception);
            return SettlementResult.fail("bank_failed", "");
        }
    }

    private static SettlementResult adapt(Object result) {
        if (result instanceof SettlementResult settlement) {
            return settlement;
        }
        if (result == null) {
            return SettlementResult.fail("bank_failed", "");
        }
        try {
            boolean success = (boolean) result.getClass().getMethod("success").invoke(result);
            String code = String.valueOf(result.getClass().getMethod("code").invoke(result));
            if (success) {
                return new SettlementResult(true, "ok", successDetail(result));
            }
            if (code == null || code.isBlank() || "null".equals(code)) {
                code = "bank_failed";
            }
            return SettlementResult.fail(code, "");
        } catch (ReflectiveOperationException exception) {
            return SettlementResult.fail("bank_failed", "");
        }
    }
    private static String successDetail(Object result) throws ReflectiveOperationException {
        try {
            return String.valueOf(result.getClass().getMethod("message").invoke(result));
        } catch (NoSuchMethodException ignored) {
            return "";
        }
    }

}
