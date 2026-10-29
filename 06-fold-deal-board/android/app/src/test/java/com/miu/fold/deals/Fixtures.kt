package com.miu.fold.deals

import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.PriceState
import com.miu.fold.deals.observations.ShippingState
import java.io.File

/** 테스트 공용 데이터 생성. 모두 가상 값이다. */
object Fixtures {
    const val NOW = 1_800_000_000_000L

    fun draft(model: String, ram: String = "12GB", storage: String = "512GB", price: String = "", ship: ShippingState = ShippingState.UNKNOWN,
              shipping: String = "", status: TradeStatus = TradeStatus.ON_SALE) =
        ListingDraft(modelRaw = model, ram = ram, storage = storage, price = price, shippingState = ship, shipping = shipping, tradeStatus = status)

    fun item(id: String, d: ListingDraft, observedAt: Long = NOW): ListingWithObservations {
        val l = d.toListing(id, null, NOW)
        val o = d.toObservationIfChanged(id, "$id-o1", null, observedAt)
        return ListingWithObservations(l, listOfNotNull(o))
    }

    fun obs(listingId: String, id: String, price: Long?, at: Long, kind: PriceKind = PriceKind.POSTED,
            ship: ShippingState = ShippingState.INCLUDED) = PriceObservation(
        id = id, listingId = listingId, priceState = if (price == null) PriceState.UNKNOWN else PriceState.KNOWN,
        amountKrw = price, rawText = price?.toString(), shippingState = ship, priceKind = kind, observedAt = at,
    )

    /** tests/fixtures/aggregate_example.csv 를 읽는다. */
    fun aggregateExample(): List<ListingWithObservations> {
        val file = listOf("../../tests/fixtures/aggregate_example.csv", "../tests/fixtures/aggregate_example.csv")
            .map(::File).first { it.exists() }
        return file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val c = splitCsv(line)
            item(c[0], draft(c[1], c[2], c[3], c[4], ShippingState.valueOf(c[5]), c[6], TradeStatus.valueOf(c[7])))
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var quoted = false
        for (ch in line) when {
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        out += sb.toString()
        return out
    }

    init { check(PriceParser.parse("1만").amountKrw == 10_000L) }
}
