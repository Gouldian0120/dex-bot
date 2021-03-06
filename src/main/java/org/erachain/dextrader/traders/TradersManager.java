package org.erachain.dextrader.traders;
// 30/03 ++

import org.erachain.dextrader.Raters.*;
import org.erachain.dextrader.controller.Controller;
import org.erachain.dextrader.settings.Settings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;


public class TradersManager {

    // for DEVELOP
    public static boolean START_ONLY_RATERS = false;

    protected static String WALLET_PASSWORD;

    private static final Logger LOGGER = LoggerFactory.getLogger(TradersManager.class);

    private List<Rater> knownRaters;
    private List<Trader> knownTraders;

    // ID -> NAME, SCALE
    protected HashMap<Long, Pair<String, Integer>> assets = new HashMap<>();

    Controller cnt;

    // for tests
    public TradersManager() {
    }

    public TradersManager(Controller cnt) {
        this.cnt = cnt;
        this.knownRaters = new ArrayList<Rater>();
        this.knownTraders = new ArrayList<Trader>();

        this.start();
    }

    public class Pair<T, U> {

        public final T a;
        public final U b;

        public Pair(T a, U b) {
            this.a = a;
            this.b = b;
        }

    }

    private void start() {

        for (Object raterName : Settings.getInstance().ratersJSON.keySet()) {
            if (raterName.equals("wex")) {
                this.knownRaters.add(new RaterWEX(this, 300));

            } else if (raterName.equals("livecoin")) {
                this.knownRaters.add(new RaterLiveCoin(this, 300));

            } else if (raterName.equals("livecoinRUR")) {
                this.knownRaters.add(new RaterLiveCoinRUR(this, 300));

            } else if (raterName.equals("polonex")) {
                JSONObject pairs = (JSONObject) Settings.getInstance().ratersJSON.get(raterName);
                int sleep = (int) (long) (Long) pairs.remove("sleep");
                this.knownRaters.add(new RaterPolonex(this, pairs, sleep));

            } else if (raterName.equals("bitforex")) {
                this.knownRaters.add(new RaterBitforexCom(this, 600));

            } else if (raterName.equals("metals-api")) {
                // for FREE rates - 1 per day!
                this.knownRaters.add(new RaterMetalsAPI(this, 60 * 60 * 24));

            } else if (raterName.equals(RaterBinanceCom.NAME)) {
                JSONObject pairs = (JSONObject) Settings.getInstance().ratersJSON.get(raterName);
                for (Object pairName: pairs.keySet()) {
                    JSONArray assetsKeys = (JSONArray) pairs.get(pairName);
                    this.knownRaters.add(new RaterBinanceCom(this, (int)(long)(Long) assetsKeys.get(2),
                            (String) pairName, (Long) assetsKeys.get(0), (Long) assetsKeys.get(1)));
                }

                //this.knownRaters.add(new RaterCross(this, 300, RaterBinanceCom.NAME,
                //        new String[]{"95.12 " + RaterBinanceCom.NAME, "12.92 " + RaterBinanceCom.NAME}));

                //this.knownRaters.add(new RaterCross(this, 350, RaterBinanceCom.NAME,
                //        new String[]{"21.95 " + RaterBinanceCom.NAME, "95.92 " + RaterBinanceCom.NAME}));

                //this.knownRaters.add(new RaterCross(this, 350, RaterBinanceCom.NAME,
                //        new String[]{"21.95 " + RaterBinanceCom.NAME, "95.12 " + RaterBinanceCom.NAME}));

            } else if (raterName.equals(RaterCoinMarketCapCom.NAME)) {
                JSONObject pairs = (JSONObject) Settings.getInstance().ratersJSON.get(raterName);
                int sleep = (int) (long) (Long) pairs.remove("sleep");
                this.knownRaters.add(new RaterCoinMarketCapCom(this, sleep, pairs));
            } else if (raterName.equals(RaterStatic.NAME)) {
                this.knownRaters.add(new RaterStatic(this, 10000, 1L, 2L, new BigDecimal("0.001")));

            } else {
                LOGGER.warn("Not found rater: " + raterName);
            }
        }

        if (START_ONLY_RATERS) {
            return;
        }

        try {
            Thread.sleep(5000);
        } catch (Exception e) {
        }

        if (Settings.getInstance().apiKeysJSON.containsKey("wallet")) {
            TradersManager.WALLET_PASSWORD = Settings.getInstance().apiKeysJSON.get("wallet").toString();
        }

        String result = cnt.apiClient.executeCommand("GET addresses/" + "?password=" + TradersManager.WALLET_PASSWORD);

        JSONArray walletAddresses = null;
        try {
            //READ JSON
            walletAddresses = (JSONArray) JSONValue.parse(result);
            LOGGER.info(walletAddresses.toJSONString());

            for (Object obj : Settings.getInstance().tradersJSON) {

                JSONObject item = (JSONObject) obj;
                String traderAddress = item.get("traderAddress").toString();
                if (!walletAddresses.contains(traderAddress)) {
                    LOGGER.error("not found traders Account - " + traderAddress);
                    continue;
                }

                String type = (String) item.get("type");
                Trader trader = null;

                if (type.equals("Guard")) {
                    trader = new StoneGuard(this, traderAddress, item);
                } else if (type.equals("GuardAbs")) {
                    trader = new StoneGuardAbs(this, traderAddress, item);
                } else if (type.equals("RandomHit")) {
                    trader = new RandomHit(this, traderAddress, item);
                } else if (type.equals("RandomHitRand")) {
                    trader = new RandomHitRand(this, traderAddress, item);
                } else if (type.equals("RandomHitSelf")) {
                    trader = new RandomHitSelf(this, traderAddress, item);
                } else if (type.equals("RandomHitSelfRand")) {
                    trader = new RandomHitSelfRand(this, traderAddress, item);
                } else if (type.equals("RandomHitSelfRandPrice")) {
                    trader = new RandomHitSelfRandPrice(this, traderAddress, item);
                } else {
                    LOGGER.warn("Not found trader: " + type);
                }

                if (trader != null) {
                    this.knownTraders.add(trader);
                }

            }

        } catch (NullPointerException | ClassCastException e) {
            //JSON EXCEPTION
            LOGGER.error(e.getMessage(), e);
            //cnt.stopAll(-11);
            //return;
        } finally {
            // CLOSE SECRET WALLET
            cnt.apiClient.executeCommand("GET wallet/lock");
        }


        if (this.knownTraders.isEmpty()) {
            LOGGER.error("Not found Traders Accounts or Traders in Settings");
            cnt.stopAll(-13);
        }
    }

    public Pair<String, Integer> getAsset(Long key) {

        if (assets.containsKey(key)) {
            return assets.get(key);
        }

        if (cnt == null || cnt.apiClient == null) {
            return null;
        }

        // IF that TRANSACTION exist in CHAIN or queue
        String result = cnt.apiClient.executeCommand("GET assets/" + key);
        try {
            //READ JSON
            JSONObject json = (JSONObject) JSONValue.parse(result);
            Pair pair = new Pair<String, Integer>(json.get("name").toString(), (int)(long)json.get("scale"));
            assets.put(key, pair);
            return pair;

        } catch (NullPointerException | ClassCastException e) {
            //JSON EXCEPTION
            ///LOGGER.error(e.getMessage(), e);
            LOGGER.error("For Asset[" + key + "] :" + result);
            return null;
        }
    }

    public void stop() {

        for (Rater rater: this.knownRaters) {
            rater.close();
            try {
                rater.join();
            } catch (Exception e) {

            }
        }
        for (Trader trader: this.knownTraders) {
            trader.close();
            try {
                trader.join();
            } catch (Exception e) {

            }
        }
    }
}
