package com.dzhokhov.currencyrates.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InputRulesTest {
    @Test fun acceptsDigitsAndOneSeparatorEitherKind() {
        assertEquals("1", InputRules.accept("", "1", Kind.FIAT))
        assertEquals("100", InputRules.accept("10", "100", Kind.FIAT))
        assertEquals("100.", InputRules.accept("100", "100.", Kind.FIAT))
        assertEquals("100.", InputRules.accept("100", "100,", Kind.FIAT))
        assertEquals("100.5", InputRules.accept("100.", "100,5", Kind.FIAT))
        assertEquals("100.55", InputRules.accept("100.5", "100.55", Kind.FIAT))
        assertEquals("007.", InputRules.accept("007", "007.", Kind.FIAT))
        assertEquals("", InputRules.accept("1", "", Kind.FIAT))
    }

    @Test fun rejectsSilentlyKeepingPrevious() {
        assertEquals("100.55", InputRules.accept("100.55", "100.555", Kind.FIAT))
        assertEquals("1.", InputRules.accept("1.", "1..", Kind.FIAT))
        assertEquals("1.5", InputRules.accept("1.5", "1.5.", Kind.FIAT))
        assertEquals("12", InputRules.accept("12", "12a", Kind.FIAT))
        assertEquals("12", InputRules.accept("12", "-12", Kind.FIAT))
        assertEquals("12", InputRules.accept("12", "1 2", Kind.FIAT))
        val twelve = "123456789012"
        assertEquals(twelve, InputRules.accept(twelve.dropLast(1), twelve, Kind.FIAT))
        assertEquals(twelve, InputRules.accept(twelve, twelve + "3", Kind.FIAT))
    }

    @Test fun eightFractionDigitsForMetalAndCrypto() {
        assertEquals("0.12345678", InputRules.accept("0.1234567", "0.12345678", Kind.CRYPTO))
        assertEquals("0.12345678", InputRules.accept("0.12345678", "0.123456789", Kind.METAL))
        assertFalse(InputRules.isValid("0.123", Kind.FIAT))
        assertTrue(InputRules.isValid("0.123", Kind.METAL))
    }

    @Test fun textToAmount() {
        assertNull(InputRules.toAmount(""))
        assertNull(InputRules.toAmount("."))
        assertEquals(BigDecimal("007"), InputRules.toAmount("007."))
        assertEquals(BigDecimal("100.5"), InputRules.toAmount("100.5"))
        assertEquals(BigDecimal("0.5"), InputRules.toAmount(".5"))
        assertEquals(0, BigDecimal("7").compareTo(InputRules.toAmount("007.")))
    }
}
