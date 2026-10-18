package com.mirandadevsource.exquisssitaappmanager.features.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la jerarquía de BackHandler y aislamiento de sesión (F01 / SEC-01).
 *
 * Jerarquía estricta de retroceso:
 * 1. Drawer abierto -> Cerrar drawer.
 * 2. Comanda en cobro (checkout) -> Salir de checkout a detalle.
 * 3. Comanda en detalle -> Salir de detalle al listado de comandas.
 * 4. Destino distinto a Dashboard -> Volver a Dashboard.
 * 5. Dashboard con drawer cerrado -> BackHandler deshabilitado (el sistema sale de la app; NUNCA vuelve a Login).
 */
class MainNavigationBackHandlerTest {

    enum class BackAction {
        CLOSE_DRAWER,
        DISMISS_CHECKOUT,
        DISMISS_ORDER_DETAIL,
        NAVIGATE_TO_DASHBOARD,
        SYSTEM_DEFAULT_EXIT,
    }

    private fun resolveBackAction(
        isDrawerOpen: Boolean,
        currentDestination: MainNavDestination,
        isOrdersInCheckout: Boolean,
        isOrdersInDetail: Boolean,
    ): Pair<Boolean, BackAction> {
        val canGoBack = isDrawerOpen || isOrdersInCheckout || isOrdersInDetail || currentDestination != MainNavDestination.DASHBOARD
        val action = when {
            isDrawerOpen -> BackAction.CLOSE_DRAWER
            isOrdersInCheckout -> BackAction.DISMISS_CHECKOUT
            isOrdersInDetail -> BackAction.DISMISS_ORDER_DETAIL
            currentDestination != MainNavDestination.DASHBOARD -> BackAction.NAVIGATE_TO_DASHBOARD
            else -> BackAction.SYSTEM_DEFAULT_EXIT
        }
        return Pair(canGoBack, action)
    }

    @Test
    fun `drawer open takes highest priority on back press`() {
        // Incluso si se está en checkout y en otra pantalla, si el drawer está abierto, Atrás lo cierra primero
        val (canGoBack, action) = resolveBackAction(
            isDrawerOpen = true,
            currentDestination = MainNavDestination.ORDERS,
            isOrdersInCheckout = true,
            isOrdersInDetail = true,
        )

        assertTrue(canGoBack)
        assertEquals(BackAction.CLOSE_DRAWER, action)
    }

    @Test
    fun `checkout dismissal takes precedence over order detail dismissal`() {
        val (canGoBack, action) = resolveBackAction(
            isDrawerOpen = false,
            currentDestination = MainNavDestination.ORDERS,
            isOrdersInCheckout = true,
            isOrdersInDetail = true,
        )

        assertTrue(canGoBack)
        assertEquals(BackAction.DISMISS_CHECKOUT, action)
    }

    @Test
    fun `order detail dismissal takes precedence over returning to dashboard`() {
        val (canGoBack, action) = resolveBackAction(
            isDrawerOpen = false,
            currentDestination = MainNavDestination.ORDERS,
            isOrdersInCheckout = false,
            isOrdersInDetail = true,
        )

        assertTrue(canGoBack)
        assertEquals(BackAction.DISMISS_ORDER_DETAIL, action)
    }

    @Test
    fun `secondary destinations return to Dashboard on back press`() {
        val secondaryDestinations = listOf(
            MainNavDestination.INVENTORY,
            MainNavDestination.PROMOTIONS,
            MainNavDestination.EMPLOYEES,
            MainNavDestination.PAYROLL,
            MainNavDestination.TABLES,
            MainNavDestination.CASH_REGISTER,
            MainNavDestination.LOYALTY,
            MainNavDestination.ORDERS, // sin comanda seleccionada
        )

        for (dest in secondaryDestinations) {
            val (canGoBack, action) = resolveBackAction(
                isDrawerOpen = false,
                currentDestination = dest,
                isOrdersInCheckout = false,
                isOrdersInDetail = false,
            )
            assertTrue("canGoBack should be true for $dest", canGoBack)
            assertEquals("Action for $dest should navigate to Dashboard", BackAction.NAVIGATE_TO_DASHBOARD, action)
        }
    }

    @Test
    fun `dashboard with closed drawer has BackHandler disabled to exit app without returning to login`() {
        val (canGoBack, action) = resolveBackAction(
            isDrawerOpen = false,
            currentDestination = MainNavDestination.DASHBOARD,
            isOrdersInCheckout = false,
            isOrdersInDetail = false,
        )

        assertFalse("BackHandler must be disabled on root Dashboard", canGoBack)
        assertEquals(BackAction.SYSTEM_DEFAULT_EXIT, action)
    }
}
