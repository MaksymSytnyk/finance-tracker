package com.maksym.financetracker.domain

class FinanceException(message: String) : IllegalArgumentException(message)

fun ensure(condition: Boolean, message: String) {
    if (!condition) throw FinanceException(message)
}

enum class Currency(val scale: Int) {
    USD(2), EUR(2), UAH(2), GBP(2), PLN(2), CHF(2), CAD(2), AUD(2), JPY(0), KWD(3), BHD(3), INR(2);
    val factor: Long get() = when (scale) { 0 -> 1; 2 -> 100; else -> 1000 }
}

fun checkedAdd(a: Long, b: Long): Long {
    val result = a + b
    ensure(((a xor result) and (b xor result)) >= 0, "Amount exceeds the supported range")
    return result
}

fun checkedNegate(value: Long): Long {
    ensure(value != Long.MIN_VALUE, "Amount exceeds the supported range")
    return -value
}

fun checkedMultiply(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0
    ensure(!(a == Long.MIN_VALUE && b == -1L) && !(b == Long.MIN_VALUE && a == -1L), "Amount exceeds the supported range")
    val result = a * b
    ensure(result / b == a, "Amount exceeds the supported range")
    return result
}

data class Money(val minor: Long, val currency: Currency) {
    init { ensure(minor != Long.MIN_VALUE, "Amount exceeds the supported range") }
    operator fun plus(other: Money): Money {
        ensure(currency == other.currency, "Choose amounts in the same currency")
        return copy(minor = checkedAdd(minor, other.minor))
    }
    operator fun minus(other: Money) = this + other.copy(minor = checkedNegate(other.minor))
    fun decimal(): String {
        val absolute = if (minor < 0) -minor else minor
        val fraction = if (currency.scale == 0) "" else "." + (absolute % currency.factor).toString().padStart(currency.scale, '0')
        return (if (minor < 0) "-" else "") + (absolute / currency.factor) + fraction
    }
    fun display(): String {
        val parts = decimal().removePrefix("-").split('.')
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        return "${currency.name} ${if (minor < 0) "-" else ""}$grouped" + if (parts.size > 1) ".${parts[1]}" else ""
    }

    companion object {
        fun parse(text: String, currency: Currency): Money {
            val value = text.trim()
            ensure(Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(value), "Enter a decimal amount, for example 12.50")
            val negative = value.startsWith('-')
            val parts = value.removePrefix("-").split('.')
            val fraction = parts.getOrElse(1) { "" }
            ensure(fraction.length <= currency.scale, "${currency.name} supports ${currency.scale} decimal places")
            val digits = (parts[0] + fraction.padEnd(currency.scale, '0')).trimStart('0').ifEmpty { "0" }
            val minor = digits.toLongOrNull() ?: throw FinanceException("Amount exceeds the supported range")
            return Money(if (negative) -minor else minor, currency)
        }
    }
}

data class Rate(
    val from: Currency,
    val to: Currency,
    val numerator: Long,
    val denominator: Long,
    val observedAt: Long,
    val source: String = "Manual",
) {
    init {
        ensure(from != to, "Choose two different currencies")
        ensure(numerator > 0 && denominator > 0, "Exchange rate must be positive")
        ensure(source.isNotBlank(), "Rate source is required")
    }

    fun convert(money: Money): Money {
        ensure(money.currency == from, "Exchange rate currency does not match")
        val negative = money.minor < 0
        val factors = longArrayOf(if (negative) -money.minor else money.minor, numerator, to.factor)
        val divisors = longArrayOf(denominator, from.factor)
        for (i in factors.indices) for (j in divisors.indices) {
            val gcd = gcd(factors[i], divisors[j])
            factors[i] /= gcd
            divisors[j] /= gcd
        }
        val top = factors.fold(1L, ::checkedMultiply)
        val bottom = divisors.fold(1L, ::checkedMultiply)
        val whole = top / bottom
        val remainder = top % bottom
        // Compare r with d-r rather than multiplying r by two, which could overflow.
        val roundUp = remainder > bottom - remainder || (remainder == bottom - remainder && whole % 2L != 0L)
        val rounded = checkedAdd(whole, if (roundUp) 1 else 0)
        return Money(if (negative) -rounded else rounded, to)
    }

    companion object {
        fun decimal(from: Currency, to: Currency, text: String, at: Long): Rate {
            ensure(Regex("[0-9]+(?:\\.[0-9]{1,9})?").matches(text.trim()), "Enter a positive rate with at most 9 decimal places")
            val parts = text.trim().split('.')
            val fraction = parts.getOrElse(1) { "" }
            val numerator = (parts[0] + fraction).toLongOrNull() ?: throw FinanceException("Rate is too large")
            val denominator = (1..fraction.length).fold(1L) { acc, _ -> acc * 10 }
            return Rate(from, to, numerator, denominator, at)
        }
    }
}

private fun gcd(a: Long, b: Long): Long {
    var x = a
    var y = b
    while (y != 0L) { val next = x % y; x = y; y = next }
    return x
}
