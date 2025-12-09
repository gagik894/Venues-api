package app.venues.platform.service

import app.venues.booking.api.dto.DirectSaleItemRequest
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.platform.api.dto.PlatformEasyItemRequest
import app.venues.platform.api.dto.PlatformEasyReserveRequest

/**
 * Mapper utilities for Platform booking flows.
 */
internal fun PlatformEasyReserveRequest.toDirectSaleRequest(): DirectSaleRequest {
    return DirectSaleRequest(
        sessionId = sessionId,
        customerEmail = customerEmail,
        customerName = customerName,
        customerPhone = customerPhone,
        items = items.map(::mapItem),
        paymentReference = null,
        promoCode = promoCode
    )
}

private fun mapItem(item: PlatformEasyItemRequest): DirectSaleItemRequest {
    return DirectSaleItemRequest(
        seatCode = item.seatCode,
        gaAreaCode = item.gaAreaCode,
        tableCode = item.tableCode,
        quantity = item.quantity
    )
}

