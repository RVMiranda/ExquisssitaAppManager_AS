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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.features.dashboard.DashboardScreen
import com.mirandadevsource.exquisssitaappmanager.features.employees.EmployeesScreen
import com.mirandadevsource.exquisssitaappmanager.features.employees.EmployeesViewModel
import com.mirandadevsource.exquisssitaappmanager.features.inventory.InventoryScreen
import com.mirandadevsource.exquisssitaappmanager.features.inventory.InventoryViewModel
import com.mirandadevsource.exquisssitaappmanager.features.loyalty.LoyaltyScanScreen
import com.mirandadevsource.exquisssitaappmanager.features.payroll.PayrollScreen
import com.mirandadevsource.exquisssitaappmanager.features.payroll.PayrollViewModel
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
    LOYALTY("Lealtad y Clientes", Icons.Outlined.QrCodeScanner),
    INVENTORY("Menú e Inventario", Icons.Outlined.Inventory2),
    EMPLOYEES("Personal y Empleados", Icons.Outlined.People, requiresManagerAccess = true),
    PAYROLL("Nómina Semanal", Icons.Outlined.Payments, requiresManagerAccess = true),
    TABLES("Mesas y Salón", Icons.Outlined.TableRestaurant),
    ORDERS("Órdenes y Comandas", Icons.AutoMirrored.Outlined.ReceiptLong),
    CASH_REGISTER("Corte de Caja", Icons.Outlined.PointOfSale, requiresManagerAccess = true),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    session: AppUserSession?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(MainNavDestination.DASHBOARD) }
    var loyaltyInitialPhone by rememberSaveable { mutableStateOf<String?>(null) }
    val employeesViewModel: EmployeesViewModel = hiltViewModel()
    val payrollViewModel: PayrollViewModel = hiltViewModel()
    val inventoryViewModel: InventoryViewModel = hiltViewModel()
    val tablesViewModel: TablesViewModel = hiltViewModel()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VanillaCustard,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(300.dp),
            ) {
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

                    Spacer(Modifier.height(16.dp))

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
                                    text = session?.employeeName?.ifBlank { "Personal" } ?: "Personal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ChocolateFondant,
                                    maxLines = 1,
                                )
                                Text(
                                    text = session?.email.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChocolateFondant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = session?.role?.displayName.orEmpty().uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandyBrown,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = ApricotIllusion, thickness = 1.dp)
                    Spacer(Modifier.height(14.dp))

                    // Elementos de Navegación del Menú Hamburguesa
                    MainNavDestination.entries.forEach { destination ->
                        if (destination.requiresManagerAccess &&
                            session?.role != UserRole.ADMIN &&
                            session?.role != UserRole.GERENTE
                        ) {
                            return@forEach
                        }

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
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = ApricotIllusion, thickness = 1.dp)
                    Spacer(Modifier.height(14.dp))

                    // Botón Cerrar Sesión
                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSignOut()
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
                TopAppBar(
                    title = {
                        Text(
                            text = currentDestination.title,
                            color = BrandyBrown,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = "Menú",
                                tint = BrandyBrown,
                            )
                        }
                    },
                    actions = {
                        if (session?.isOffline == true) {
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
                        DashboardScreen(
                            session = session,
                            onNavigateToLoyalty = { phoneQuery ->
                                loyaltyInitialPhone = phoneQuery
                                currentDestination = MainNavDestination.LOYALTY
                            },
                        )
                    }
                    MainNavDestination.LOYALTY -> {
                        LoyaltyScanScreen(
                            initialPhoneQuery = loyaltyInitialPhone,
                            onBackToDashboard = {
                                currentDestination = MainNavDestination.DASHBOARD
                            },
                        )
                    }
                    MainNavDestination.INVENTORY -> {
                        InventoryScreen(viewModel = inventoryViewModel)
                    }
                    MainNavDestination.EMPLOYEES -> {
                        EmployeesScreen(viewModel = employeesViewModel)
                    }
                    MainNavDestination.PAYROLL -> {
                        PayrollScreen(viewModel = payrollViewModel)
                    }
                    MainNavDestination.TABLES -> {
                        TablesScreen(viewModel = tablesViewModel)
                    }
                    MainNavDestination.ORDERS,
                    MainNavDestination.CASH_REGISTER -> {
                        ModulePlaceholderScreen(title = currentDestination.title)
                    }
                }
            }
        }
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
