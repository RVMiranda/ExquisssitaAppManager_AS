package com.mirandadevsource.exquisssitaappmanager.features.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.features.dashboard.DashboardScreen
import com.mirandadevsource.exquisssitaappmanager.features.dashboard.DashboardViewModel
import com.mirandadevsource.exquisssitaappmanager.features.employees.EmployeesScreen
import com.mirandadevsource.exquisssitaappmanager.features.employees.EmployeesViewModel
import com.mirandadevsource.exquisssitaappmanager.features.cash_register.CashRegisterScreen
import com.mirandadevsource.exquisssitaappmanager.features.cash_register.CashRegisterViewModel
import com.mirandadevsource.exquisssitaappmanager.features.inventory.InventoryScreen
import com.mirandadevsource.exquisssitaappmanager.features.inventory.InventoryViewModel
import com.mirandadevsource.exquisssitaappmanager.features.loyalty.LoyaltyScanScreen
import com.mirandadevsource.exquisssitaappmanager.features.loyalty.LoyaltyViewModel
import com.mirandadevsource.exquisssitaappmanager.features.payroll.PayrollScreen
import com.mirandadevsource.exquisssitaappmanager.features.payroll.PayrollViewModel
import com.mirandadevsource.exquisssitaappmanager.features.promotions.PromotionsScreen
import com.mirandadevsource.exquisssitaappmanager.features.promotions.PromotionsViewModel
import androidx.compose.material.icons.outlined.CardGiftcard
import com.mirandadevsource.exquisssitaappmanager.features.orders.OrderViewModel
import com.mirandadevsource.exquisssitaappmanager.features.orders.OrdersScreen
import com.mirandadevsource.exquisssitaappmanager.features.rewards.RewardsCatalogScreen
import com.mirandadevsource.exquisssitaappmanager.features.rewards.RewardsCatalogViewModel
import com.mirandadevsource.exquisssitaappmanager.features.tables.TablesScreen
import com.mirandadevsource.exquisssitaappmanager.features.tables.TablesViewModel
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import kotlinx.coroutines.launch

enum class MainNavDestination(
    val title: String,
    val icon: ImageVector,
    val requiresManagerAccess: Boolean = false,
) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    LOYALTY("Lealtad y QR", Icons.Outlined.QrCodeScanner),
    INVENTORY("Menú e Inventario", Icons.Outlined.Inventory2),
    PROMOTIONS("Promociones y Ofertas", Icons.Outlined.Campaign, requiresManagerAccess = true),
    REWARDS("Recompensas", Icons.Outlined.CardGiftcard, requiresManagerAccess = true),
    EMPLOYEES("Personal y Empleados", Icons.Outlined.People, requiresManagerAccess = true),
    PAYROLL("Nómina Semanal", Icons.Outlined.Payments, requiresManagerAccess = true),
    TABLES("Mesas y Salón", Icons.Outlined.TableRestaurant),
    ORDERS("Órdenes y Comandas", Icons.AutoMirrored.Outlined.ReceiptLong),
    CASH_REGISTER("Corte de Caja", Icons.Outlined.PointOfSale, requiresManagerAccess = true),
}

private data class DrawerNavGroup(
    val title: String?,
    val destinations: List<MainNavDestination>,
)

private val DrawerNavGroups = listOf(
    DrawerNavGroup(
        title = null,
        destinations = listOf(MainNavDestination.DASHBOARD),
    ),
    DrawerNavGroup(
        title = "Órdenes",
        destinations = listOf(
            MainNavDestination.ORDERS,
            MainNavDestination.INVENTORY,
            MainNavDestination.TABLES,
        ),
    ),
    DrawerNavGroup(
        title = "Clientes",
        destinations = listOf(
            MainNavDestination.LOYALTY,
            MainNavDestination.PROMOTIONS,
            MainNavDestination.REWARDS,
        ),
    ),
    DrawerNavGroup(
        title = "Personal",
        destinations = listOf(
            MainNavDestination.EMPLOYEES,
            MainNavDestination.PAYROLL,
        ),
    ),
    DrawerNavGroup(
        title = null,
        destinations = listOf(
            MainNavDestination.CASH_REGISTER,
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    session: AppUserSession,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(MainNavDestination.DASHBOARD) }
    var loyaltyInitialPhone by rememberSaveable { mutableStateOf<String?>(null) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    // ordersViewModel se comparte a nivel de scaffold para sincronizar la topbar contextual, back navigation y mesas
    val ordersViewModel: OrderViewModel = hiltViewModel()
    val ordersUiState by ordersViewModel.uiState.collectAsStateWithLifecycle()

    // Jerarquía de BackHandler: cerrar drawer -> salir de checkout -> salir de detalle -> volver a Dashboard
    val isOrdersInDetail = currentDestination == MainNavDestination.ORDERS && ordersUiState.selectedOrder != null
    val isOrdersInCheckout = currentDestination == MainNavDestination.ORDERS && ordersUiState.activeCheckoutOrder != null
    val canGoBack = drawerState.isOpen || isOrdersInCheckout || isOrdersInDetail || currentDestination != MainNavDestination.DASHBOARD

    BackHandler(enabled = canGoBack) {
        when {
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            isOrdersInCheckout -> {
                ordersViewModel.dismissCheckout()
            }
            isOrdersInDetail -> {
                ordersViewModel.dismissOrderDetail()
            }
            currentDestination != MainNavDestination.DASHBOARD -> {
                currentDestination = MainNavDestination.DASHBOARD
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VanillaCustard,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(300.dp),
            ) {
                val drawerScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    // Encabezado del Menú Lateral
                    Text(
                        text = "La Exquisssita",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Panel Operativo",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(14.dp))

                    // Ficha de Empleado / Perfil
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ApricotIllusion.copy(alpha = 0.35f), shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = BrandyBrown,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.employeeName.ifBlank { "Personal" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ChocolateFondant,
                                    maxLines = 1,
                                )
                                Text(
                                    text = session.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChocolateFondant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = session.role.displayName.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandyBrown,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = ApricotIllusion, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))

                    // Elementos de Navegación Agrupados por Categoría
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(drawerScrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val isManagerOrAdmin = session.role == UserRole.ADMIN || session.role == UserRole.GERENTE

                        DrawerNavGroups.forEachIndexed { index, group ->
                            val visibleItems = group.destinations.filter { !it.requiresManagerAccess || isManagerOrAdmin }
                            if (visibleItems.isNotEmpty()) {
                                if (group.title != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = group.title.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ChocolateFondant.copy(alpha = 0.55f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    )
                                } else if (index > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.5f), thickness = 1.dp)
                                    Spacer(Modifier.height(6.dp))
                                }

                                visibleItems.forEach { destination ->
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                text = destination.title,
                                                fontWeight = if (currentDestination == destination) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.title,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        },
                                        selected = currentDestination == destination,
                                        onClick = {
                                            currentDestination = destination
                                            loyaltyInitialPhone = null
                                            scope.launch { drawerState.close() }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = ApricotIllusion.copy(alpha = 0.4f),
                                            unselectedContainerColor = Color.Transparent,
                                            selectedIconColor = BrandyBrown,
                                            unselectedIconColor = ChocolateFondant,
                                            selectedTextColor = BrandyBrown,
                                            unselectedTextColor = ChocolateFondant,
                                        ),
                                        modifier = Modifier.padding(vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = ApricotIllusion, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    // Botón Cerrar Sesión
                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSignOutConfirmation = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandyBrown,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Cerrar sesión",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Cerrar sesión",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                val isOrders = currentDestination == MainNavDestination.ORDERS
                val activeCheckout = if (isOrders) ordersUiState.activeCheckoutOrder else null
                val selectedOrder = if (isOrders) ordersUiState.selectedOrder else null

                TopAppBar(
                    title = {
                        when {
                            activeCheckout != null -> {
                                Column {
                                    Text(
                                        text = "Cobro de Cuenta",
                                        color = BrandyBrown,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    val serviceLabel = when (activeCheckout.serviceType) {
                                        ServiceType.TABLE -> "Mesa ${ordersUiState.tableMap[activeCheckout.tableId]?.number ?: "Asignada"}"
                                        ServiceType.TAKEOUT -> "Para Llevar"
                                        ServiceType.DELIVERY -> "Domicilio"
                                    }
                                    Text(
                                        text = "Comanda #${activeCheckout.id.take(8).uppercase()} • $serviceLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChocolateFondant.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            selectedOrder != null -> {
                                Column {
                                    Text(
                                        text = "Comanda #${selectedOrder.id.take(8).uppercase()}",
                                        color = BrandyBrown,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    val serviceLabel = when (selectedOrder.serviceType) {
                                        ServiceType.TABLE -> "Mesa ${ordersUiState.tableMap[selectedOrder.tableId]?.number ?: "Asignada"}"
                                        ServiceType.TAKEOUT -> "Para Llevar"
                                        ServiceType.DELIVERY -> "Domicilio"
                                    }
                                    Text(
                                        text = "$serviceLabel • ${selectedOrder.status.databaseValue}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChocolateFondant.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = currentDestination.title,
                                    color = BrandyBrown,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        when {
                            activeCheckout != null -> {
                                IconButton(onClick = { ordersViewModel.dismissCheckout() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Volver",
                                        tint = BrandyBrown,
                                    )
                                }
                            }
                            selectedOrder != null -> {
                                IconButton(onClick = { ordersViewModel.dismissOrderDetail() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Volver",
                                        tint = BrandyBrown,
                                    )
                                }
                            }
                            else -> {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Menu,
                                        contentDescription = "Menú",
                                        tint = BrandyBrown,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (session.isOffline) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, OfflineAmber),
                                color = OfflineAmber.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = OfflineAmber,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Offline",
                                        color = OfflineAmber,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VanillaCustard,
                    ),
                )
            },
            containerColor = VanillaCustard,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when (currentDestination) {
                    MainNavDestination.DASHBOARD -> {
                        val dashboardViewModel: DashboardViewModel = hiltViewModel()
                        DashboardScreen(
                            session = session,
                            viewModel = dashboardViewModel,
                            onNavigateToLoyalty = {
                                currentDestination = MainNavDestination.LOYALTY
                            },
                            onNavigateToTables = {
                                currentDestination = MainNavDestination.TABLES
                            },
                            onNavigateToOrders = {
                                currentDestination = MainNavDestination.ORDERS
                            },
                            onNavigateToCashRegister = {
                                currentDestination = MainNavDestination.CASH_REGISTER
                            },
                            onNavigateToInventory = {
                                currentDestination = MainNavDestination.INVENTORY
                            },
                            onNavigateToPayroll = {
                                currentDestination = MainNavDestination.PAYROLL
                            },
                        )
                    }
                    MainNavDestination.LOYALTY -> {
                        val loyaltyViewModel: LoyaltyViewModel = hiltViewModel()
                        LoyaltyScanScreen(
                            viewModel = loyaltyViewModel,
                            initialPhoneQuery = loyaltyInitialPhone,
                            onBackToDashboard = {
                                currentDestination = MainNavDestination.DASHBOARD
                            },
                        )
                    }
                    MainNavDestination.INVENTORY -> {
                        val inventoryViewModel: InventoryViewModel = hiltViewModel()
                        InventoryScreen(viewModel = inventoryViewModel)
                    }
                    MainNavDestination.PROMOTIONS -> {
                        val promotionsViewModel: PromotionsViewModel = hiltViewModel()
                        PromotionsScreen(viewModel = promotionsViewModel)
                    }
                    MainNavDestination.REWARDS -> {
                        val rewardsViewModel: RewardsCatalogViewModel = hiltViewModel()
                        RewardsCatalogScreen(
                            viewModel = rewardsViewModel,
                            onBackToDashboard = {
                                currentDestination = MainNavDestination.DASHBOARD
                            },
                        )
                    }
                    MainNavDestination.EMPLOYEES -> {
                        val employeesViewModel: EmployeesViewModel = hiltViewModel()
                        EmployeesScreen(viewModel = employeesViewModel)
                    }
                    MainNavDestination.PAYROLL -> {
                        val payrollViewModel: PayrollViewModel = hiltViewModel()
                        PayrollScreen(viewModel = payrollViewModel)
                    }
                    MainNavDestination.TABLES -> {
                        val tablesViewModel: TablesViewModel = hiltViewModel()
                        TablesScreen(
                            viewModel = tablesViewModel,
                            onOpenOrderForTable = { tableId ->
                                ordersViewModel.openNewOrderDialog(initialTableId = tableId)
                                currentDestination = MainNavDestination.ORDERS
                            },
                            onViewOrder = { orderId ->
                                ordersViewModel.selectOrder(orderId)
                                currentDestination = MainNavDestination.ORDERS
                            },
                        )
                    }
                    MainNavDestination.ORDERS -> {
                        OrdersScreen(
                            viewModel = ordersViewModel,
                            onNavigateToTables = {
                                currentDestination = MainNavDestination.TABLES
                            },
                        )
                    }
                    MainNavDestination.CASH_REGISTER -> {
                        val cashRegisterViewModel: CashRegisterViewModel = hiltViewModel()
                        CashRegisterScreen(
                            viewModel = cashRegisterViewModel,
                            onNavigateToOrders = {
                                currentDestination = MainNavDestination.ORDERS
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = {
                Text(
                    text = "¿Cerrar sesión?",
                    color = BrandyBrown,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Se cerrará el panel operativo en este dispositivo. Las operaciones pendientes de sincronización quedarán protegidas para este usuario y se reanudarán al volver a iniciar sesión.",
                    color = ChocolateFondant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmation = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Cerrar sesión", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Cancelar", color = ChocolateFondant)
                }
            },
            containerColor = VanillaCustard,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun ModulePlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Construction,
                contentDescription = null,
                tint = BrandyBrown,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Este módulo operativo se habilitará en la siguiente fase de desarrollo.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
        }
    }
}
