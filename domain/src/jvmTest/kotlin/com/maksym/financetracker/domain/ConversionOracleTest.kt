package com.maksym.financetracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionOracleTest {
    @Test fun exactConversionMatchesIndependentDecimalOracle() {
        val random = Random(7821)
        repeat(10000) {
            val source = Currency.entries[random.nextInt(Currency.entries.size)]
            val target = Currency.entries.filterNot { it == source }.random(random)
            val minor = random.nextLong(-1_000_000_000, 1_000_000_000)
            val numerator = random.nextLong(1, 10000)
            val denominator = random.nextLong(1, 10000)
            val expected = BigDecimal.valueOf(minor).multiply(BigDecimal.valueOf(numerator)).multiply(BigDecimal.valueOf(target.factor))
                .divide(BigDecimal.valueOf(denominator).multiply(BigDecimal.valueOf(source.factor)), 0, RoundingMode.HALF_EVEN).longValueExact()
            assertEquals(expected, Rate(source, target, numerator, denominator, 0).convert(Money(minor, source)).minor)
        }
    }
}
