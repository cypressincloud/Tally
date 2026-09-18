package com.example.budgetapp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CurrencyUtilsTest {
    @Test
    public void symbolToCode_keepsAmbiguousNordicCurrenciesDistinct() {
        assertEquals("SEK", CurrencyUtils.symbolToCode("kr"));
        assertEquals("NOK", CurrencyUtils.symbolToCode("NOK"));
        assertEquals("DKK", CurrencyUtils.symbolToCode("DKK"));
        assertEquals("ISK", CurrencyUtils.symbolToCode("ISK"));
    }

    @Test
    public void symbolToCode_mapsJapaneseYenCorrectly() {
        assertEquals("JPY", CurrencyUtils.symbolToCode("JP¥"));
        assertEquals("CNY", CurrencyUtils.symbolToCode(null));
    }
}
