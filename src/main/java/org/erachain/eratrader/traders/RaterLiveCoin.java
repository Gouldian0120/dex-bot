package org.erachain.eratrader.traders;
// 30/03 ++

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.math.BigDecimal;


public class RaterLiveCoin extends Rater {

    public RaterLiveCoin(TradersManager tradersManager, int sleepSec) {
        super(tradersManager, "livecoin", sleepSec);

        this.apiURL = "https://api.livecoin.net/exchange/ticker?currencyPair=ETH/BTC";

    }

    protected void parse(String result) {
        JSONObject json = null;
        try {
            //READ JSON
            json = (JSONObject) JSONValue.parse(result);
        } catch (NullPointerException | ClassCastException e) {
            //JSON EXCEPTION
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        if (json == null)
            return;

        JSONObject pair;
        BigDecimal price;

        if (json.containsKey("symbol")
                && "ETH/BTC".equals((String)json.get("symbol"))) {
            price = new BigDecimal(json.get("vwap").toString()).setScale(10, BigDecimal.ROUND_HALF_UP);
            price = price.multiply(this.shiftRate).setScale(10, BigDecimal.ROUND_HALF_UP);
            if (cnt.DEVELOP_USE) {
                setRate(1105L, 1104L, this.courseName, price);
            } else {
                setRate(14L, 12L, this.courseName, price);
            }
        }

    }
}
