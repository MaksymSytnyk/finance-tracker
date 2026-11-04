package com.maksym.financetracker.data

import com.maksym.financetracker.domain.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*

/** Explicit DTO mapping keeps serialization concerns out of the financial domain. */
object BackupCodec {
    private val json = Json { prettyPrint = true }
    private const val MAX_BYTES = 20 * 1024 * 1024

    fun encode(ledger: Ledger): String = json.encodeToString(JsonObject.serializer(), obj(
        "format" to 1, "currencyScales" to JsonObject(Currency.entries.associate { it.name to JsonPrimitive(it.scale) }),
        "accounts" to array(ledger.accounts.map(::account)), "categories" to array(ledger.categories.map(::category)),
        "transactions" to array(ledger.transactions.map(::transaction)), "budgets" to array(ledger.budgets.map(::budget)),
        "rules" to array(ledger.rules.map(::rule)), "occurrences" to array(ledger.occurrences.map { obj("ruleId" to it.ruleId, "date" to it.date.toString(), "transactionId" to it.transactionId) }),
        "rates" to array(ledger.rates.map { obj("from" to it.from.name, "to" to it.to.name, "numerator" to it.numerator.toString(), "denominator" to it.denominator.toString(), "observedAt" to it.observedAt.toString(), "source" to it.source) }),
        "preferences" to obj("currency" to ledger.preferences.currency.name, "zone" to ledger.preferences.zone, "theme" to ledger.preferences.theme.name),
    ))

    fun decode(text: String): Ledger {
        ensure(text.length <= MAX_BYTES && text.encodeToByteArray().size <= MAX_BYTES, "Backup exceeds the 20 MB limit")
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            ensure(root.long("format") == 1L, "This backup version is not supported")
            val scales = root.getValue("currencyScales").jsonObject
            Currency.entries.forEach { ensure(scales.long(it.name) == it.scale.toLong(), "Backup currency precision does not match") }
            val preferences = root.getValue("preferences").jsonObject
            Ledger(
                accounts = root.rows("accounts").map(::readAccount), categories = root.rows("categories").map(::readCategory),
                transactions = root.rows("transactions").map(::readTransaction), budgets = root.rows("budgets").map(::readBudget),
                rules = root.rows("rules").map(::readRule),
                occurrences = root.rows("occurrences").map { Occurrence(it.str("ruleId"), it.date("date"), it.optional("transactionId")) },
                rates = root.rows("rates").map { Rate(Currency.valueOf(it.str("from")), Currency.valueOf(it.str("to")), it.long("numerator"), it.long("denominator"), it.long("observedAt"), it.str("source")) },
                preferences = Preferences(Currency.valueOf(preferences.str("currency")), preferences.str("zone"), ThemeMode.valueOf(preferences.str("theme"))),
            ).also(::validateLedger)
        } catch (e: FinanceException) { throw e }
        catch (_: Exception) { throw FinanceException("Backup is malformed or contains invalid data") }
    }

    fun transactionText(tx: Transaction) = transaction(tx).toString()
    fun parseTransaction(text: String) = readTransaction(json.parseToJsonElement(text).jsonObject)
    fun tagsText(tags: Set<String>) = JsonArray(tags.sorted().map(::JsonPrimitive)).toString()
    fun parseTags(text: String) = json.parseToJsonElement(text).jsonArray.map { it.jsonPrimitive.content }.toSet()

    private fun account(a: Account) = obj("id" to a.id, "name" to a.name, "currency" to a.currency.name, "type" to a.type.name, "openingMinor" to a.openingMinor.toString(), "openingDate" to a.openingDate.toString(), "archived" to a.archived, "createdAt" to a.createdAt.toString(), "updatedAt" to a.updatedAt.toString())
    private fun readAccount(o: JsonObject) = Account(o.str("id"), o.str("name"), Currency.valueOf(o.str("currency")), AccountType.valueOf(o.str("type")), o.long("openingMinor"), o.date("openingDate"), o.bool("archived"), o.long("createdAt"), o.long("updatedAt"))
    private fun category(c: Category) = obj("id" to c.id, "name" to c.name, "type" to c.type.name, "icon" to c.icon, "color" to c.color.toString(), "archived" to c.archived)
    private fun readCategory(o: JsonObject) = Category(o.str("id"), o.str("name"), TransactionType.valueOf(o.str("type")), o.str("icon"), o.long("color"), o.bool("archived"))
    private fun budget(b: Budget) = obj("id" to b.id, "categoryId" to b.categoryId, "month" to b.month.toString(), "minor" to b.limit.minor.toString(), "currency" to b.limit.currency.name)
    private fun readBudget(o: JsonObject) = Budget(o.str("id"), o.str("categoryId"), o.date("month"), Money(o.long("minor"), Currency.valueOf(o.str("currency"))))
    private fun transaction(t: Transaction) = obj("id" to t.id, "type" to t.type.name, "accountId" to t.accountId, "minor" to t.amount.minor.toString(), "currency" to t.amount.currency.name, "date" to t.date.toString(), "categoryId" to t.categoryId, "destinationId" to t.destinationId, "receivedMinor" to t.received?.minor?.toString(), "receivedCurrency" to t.received?.currency?.name, "description" to t.description, "notes" to t.notes, "tags" to JsonArray(t.tags.sorted().map(::JsonPrimitive)), "createdAt" to t.createdAt.toString(), "updatedAt" to t.updatedAt.toString(), "rateSource" to t.rateSource, "rateObservedAt" to t.rateObservedAt?.toString())
    private fun readTransaction(o: JsonObject) = Transaction(o.str("id"), TransactionType.valueOf(o.str("type")), o.str("accountId"), Money(o.long("minor"), Currency.valueOf(o.str("currency"))), o.date("date"), o.optional("categoryId"), o.optional("destinationId"), o.optional("receivedMinor")?.let { Money(it.toLong(), Currency.valueOf(o.str("receivedCurrency"))) }, o.str("description"), o.str("notes"), o.getValue("tags").jsonArray.map { it.jsonPrimitive.content }.toSet(), o.long("createdAt"), o.long("updatedAt"), o.optional("rateSource"), o.optional("rateObservedAt")?.toLong())
    private fun rule(r: RecurringRule) = obj("id" to r.id, "template" to transaction(r.template), "frequency" to r.frequency.name, "interval" to r.interval, "anchor" to r.anchor.toString(), "nextIndex" to r.nextIndex, "zone" to r.zone, "endDate" to r.endDate?.toString(), "active" to r.active)
    private fun readRule(o: JsonObject) = RecurringRule(o.str("id"), readTransaction(o.getValue("template").jsonObject), Frequency.valueOf(o.str("frequency")), o.long("interval").toIntExact(), o.date("anchor"), o.long("nextIndex").toIntExact(), o.str("zone"), o.optional("endDate")?.let(LocalDate::parse), o.bool("active"))
    private fun Long.toIntExact(): Int { ensure(this in 0..Int.MAX_VALUE.toLong(), "Integer is outside supported range"); return toInt() }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = str(key).toLong()
    private fun JsonObject.bool(key: String) = getValue(key).jsonPrimitive.boolean
    private fun JsonObject.date(key: String) = LocalDate.parse(str(key))
    private fun JsonObject.optional(key: String) = getValue(key).jsonPrimitive.contentOrNull
    private fun JsonObject.rows(key: String) = getValue(key).jsonArray.map { it.jsonObject }
    private fun array(values: List<JsonObject>) = JsonArray(values)
    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (key, value) -> key to when (value) { null -> JsonNull; is JsonElement -> value; is Boolean -> JsonPrimitive(value); is Number -> JsonPrimitive(value); else -> JsonPrimitive(value.toString()) } })
}

object CsvExport {
    fun encode(ledger: Ledger): String {
        fun cell(text: String, userText: Boolean = false): String {
            val safe = if (userText && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n')) "'$text" else text
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val header = "id,date,type,account,currency,amount,category,destination_account,destination_currency,received_amount,description,notes,tags,created_at,updated_at,rate_source,rate_observed_at"
        return header + "\r\n" + ledger.transactions.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id }).joinToString("\r\n") { tx ->
            listOf(cell(tx.id), cell(tx.date.toString()), cell(tx.type.name), cell(ledger.accounts.first { it.id == tx.accountId }.name, true), cell(tx.amount.currency.name), cell(tx.amount.decimal()), cell(ledger.categories.find { it.id == tx.categoryId }?.name.orEmpty(), true), cell(ledger.accounts.find { it.id == tx.destinationId }?.name.orEmpty(), true), cell(tx.received?.currency?.name.orEmpty()), cell(tx.received?.decimal().orEmpty()), cell(tx.description, true), cell(tx.notes, true), cell(tx.tags.sorted().joinToString(";"), true), cell(tx.createdAt.toString()), cell(tx.updatedAt.toString()), cell(tx.rateSource.orEmpty(), true), cell(tx.rateObservedAt?.toString().orEmpty())).joinToString(",")
        } + "\r\n"
    }
}
