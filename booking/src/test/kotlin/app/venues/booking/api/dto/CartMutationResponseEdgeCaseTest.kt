package app.venues.booking.api.dto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Edge case tests for CartMutationResponse to verify proper handling of failure scenarios.
 */
class CartMutationResponseEdgeCaseTest {

    @Test
    fun `CartMutationResponse with success false indicates operation failure`() {
        val response = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = false,  // Operation failed
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT
        )

        assertFalse(response.success, "success should be false for failed operations")
        assertNotNull(response.cartToken, "cartToken should still be present even on failure")
    }

    @Test
    fun `CartMutationResponse defaults to success true`() {
        val response = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT
        )

        assertTrue(response.success, "success should default to true")
    }

    @Test
    fun `CartMutationResponse supports all item types`() {
        val seatResponse = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = true,
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT
        )

        val gaResponse = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = true,
            affectedItemId = "GA-FLOOR",
            affectedItemType = CartItemType.GA
        )

        val tableResponse = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = true,
            affectedItemId = "TABLE-1",
            affectedItemType = CartItemType.TABLE
        )

        assertEquals(CartItemType.SEAT, seatResponse.affectedItemType)
        assertEquals(CartItemType.GA, gaResponse.affectedItemType)
        assertEquals(CartItemType.TABLE, tableResponse.affectedItemType)
    }

    @Test
    fun `CartMutationResponse with null cartVersion`() {
        val response = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = true,
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT,
            cartVersion = null
        )

        assertNull(response.cartVersion, "cartVersion is optional and can be null")
    }

    @Test
    fun `CartMutationResponse with explicit cartVersion for optimistic locking`() {
        val version = 123L
        val response = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = true,
            affectedItemId = "A1",
            affectedItemType = CartItemType.SEAT,
            cartVersion = version
        )

        assertEquals(version, response.cartVersion, "cartVersion should be preserved for optimistic locking")
    }

    /**
     * IMPORTANT: This test documents a potential bug in CartService.holdBatch().
     * 
     * The holdBatch method does not check `result.success` before using `result.cartToken`.
     * If a mutation operation fails (success=false), the code continues processing
     * as if it succeeded, potentially leading to data inconsistencies.
     * 
     * TODO: Add validation in CartService.holdBatch() to check success flag:
     * ```
     * if (!result.success) {
     *     throw VenuesException.BadRequest("Failed to add item: ${result.affectedItemId}")
     * }
     * ```
     */
    @Test
    fun `documents potential bug - CartService should check success flag`() {
        // This test documents expected behavior that is NOT currently implemented
        val failedResponse = CartMutationResponse(
            cartToken = UUID.randomUUID(),
            success = false,
            affectedItemId = "INVALID-SEAT",
            affectedItemType = CartItemType.SEAT
        )

        // Currently, CartService.holdBatch() would use failedResponse.cartToken
        // without checking failedResponse.success, which could lead to issues.
        // 
        // Expected behavior: Service should detect success=false and throw exception
        // Actual behavior: Service blindly uses cartToken regardless of success flag

        assertFalse(failedResponse.success, "Failed operations should have success=false")

        // This comment serves as documentation that this edge case needs handling
        println("⚠️  WARNING: CartService.holdBatch() does not validate CartMutationResponse.success flag")
        println("   This could lead to data inconsistencies if mutation operations fail silently")
    }
}
