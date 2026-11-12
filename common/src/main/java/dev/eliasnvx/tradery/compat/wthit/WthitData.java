package dev.eliasnvx.tradery.compat.wthit;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipData;
import mcp.mobius.waila.api.IData;

/** What the server adds to WTHIT's tooltip data: a vending block's stock and a bell's village. */
final class WthitData {
    private WthitData() {
    }

    /** Trades in stock (or room for buyback) of a vending block. */
    record Stock(int stock) implements IData {
        static final IData.Type<Stock> TYPE = IData.createType(Tradery.id("vending_stock"));

        @Override
        public IData.Type<Stock> type() {
            return TYPE;
        }
    }

    /** A bell's village. */
    record Bell(TooltipData.Bell bell) implements IData {
        static final IData.Type<Bell> TYPE = IData.createType(Tradery.id("bell_village"));

        @Override
        public IData.Type<Bell> type() {
            return TYPE;
        }
    }
}
