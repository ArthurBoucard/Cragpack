package app.cragpack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.cragpack.data.AppGraph
import app.cragpack.data.SyncState
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.ThemeMode
import app.cragpack.domain.settings.Theme
import app.cragpack.feature.LocalGradeDisplay
import app.cragpack.feature.LocalSyncStatus
import app.cragpack.feature.toStatus
import app.cragpack.feature.account.AccountScreen
import app.cragpack.feature.account.LocalAccount
import app.cragpack.feature.account.AccountViewModel
import app.cragpack.feature.crew.CrewScreen
import app.cragpack.feature.crew.CrewViewModel
import app.cragpack.feature.join.JoinScreen
import app.cragpack.feature.join.JoinViewModel
import app.cragpack.feature.gear.GearViewModel
import app.cragpack.feature.logbook.LogSendScreen
import app.cragpack.feature.logbook.LogSendViewModel
import app.cragpack.feature.logbook.LogbookScreen
import app.cragpack.feature.logbook.LogbookViewModel
import app.cragpack.feature.newtrip.NewTripScreen
import app.cragpack.feature.newtrip.NewTripViewModel
import app.cragpack.feature.objective.ObjectiveFormScreen
import app.cragpack.feature.objective.ObjectiveFormViewModel
import app.cragpack.feature.objective.ObjectiveScreen
import app.cragpack.feature.objective.ObjectiveViewModel
import app.cragpack.feature.plan.ConflictViewModel
import app.cragpack.feature.plan.EditDayScreen
import app.cragpack.feature.plan.EditDayViewModel
import app.cragpack.feature.plan.PlanViewModel
import app.cragpack.feature.recap.RecapScreen
import app.cragpack.feature.recap.RecapViewModel
import app.cragpack.feature.settings.SettingsScreen
import app.cragpack.feature.settings.SettingsViewModel
import app.cragpack.feature.trip.TripScreen
import app.cragpack.feature.trip.TripViewModel
import app.cragpack.feature.triplog.TripLogViewModel
import app.cragpack.feature.trips.TripsScreen
import app.cragpack.feature.trips.TripsViewModel
import app.cragpack.feature.tripsettings.TripSettingsScreen
import app.cragpack.feature.tripsettings.TripSettingsViewModel
import app.cragpack.feature.wishlist.WishlistViewModel
import kotlinx.serialization.Serializable

@Serializable private data object TripsRoute
@Serializable private data object NewTripRoute
@Serializable private data class TripRoute(val id: String)
@Serializable private data object SettingsRoute
@Serializable private data class RecapRoute(val tripId: String)
@Serializable private data class TripSettingsRoute(val tripId: String)
@Serializable private data class ObjectiveFormRoute(val tripId: String, val objectiveId: String? = null)
@Serializable private data class ObjectiveRoute(val id: String)
@Serializable private data object LogbookRoute
@Serializable private data class EditDayRoute(val tripId: String, val dayId: String)
@Serializable private data object LogSendRoute
@Serializable private data object AccountRoute
@Serializable private data class CrewRoute(val tripId: String, val invite: Boolean = false)
@Serializable private data class JoinRoute(val token: String)

/** Root of the app on both platforms. Trips home is the root (docs/03 navigation model). */
@Composable
fun App(graph: AppGraph) {
    // Read once before the first frame, so a chosen theme doesn't flash the system one.
    val settings by remember { graph.settings.observe() }.collectAsState(remember { graph.settings.current() })
    val mode = when (settings.theme) {
        Theme.Auto -> ThemeMode.Auto
        Theme.Dark -> ThemeMode.Dark
        Theme.Sunlight -> ThemeMode.Sunlight
    }
    val sync by remember { graph.sync.observe() }.collectAsState(SyncState(signedIn = false, pending = 0))
    CragpackTheme(mode) {
        CompositionLocalProvider(
            LocalGradeDisplay provides settings.gradeDisplay,
            LocalSyncStatus provides sync.toStatus(),
            LocalAccount provides graph.account,
        ) { AppNav(graph) }
    }
}

@Composable
private fun AppNav(graph: AppGraph) {
    val nav = rememberNavController()
    // An invite link the app was opened with: show Join on top of whatever is open.
    val opened by graph.openedInvite.collectAsState()
    LaunchedEffect(opened) {
        // Not nav.open: a link must never be dropped because a transition is running.
        opened?.let { nav.navigate(JoinRoute(it)) { launchSingleTop = true }; graph.openedInvite.value = null }
    }
    NavHost(nav, startDestination = TripsRoute) {
        composable<TripsRoute> {
            TripsScreen(
                viewModel { TripsViewModel(graph) },
                onNewTrip = { nav.open(NewTripRoute) },
                onOpenTrip = { nav.open(TripRoute(it)) },
                onRecap = { nav.open(RecapRoute(it)) },
                onLogbook = { nav.open(LogbookRoute) },
                onSettings = { nav.open(SettingsRoute) },
                onJoin = { nav.open(JoinRoute(it)) },
            )
        }
        composable<CrewRoute> { entry ->
            val (tripId, invite) = entry.toRoute<CrewRoute>()
            CrewScreen(
                viewModel { CrewViewModel(graph, tripId) },
                invite = invite,
                onBack = { nav.back() },
                onSettings = { nav.open(TripSettingsRoute(tripId)) },
                onCreateAccount = { nav.open(AccountRoute) },
            )
        }
        composable<JoinRoute> { entry ->
            val token = entry.toRoute<JoinRoute>().token
            JoinScreen(
                viewModel { JoinViewModel(graph, token) },
                // Not nav.open: these fire once, possibly while the account screen is still closing.
                onJoined = { nav.navigate(TripRoute(it)) { popUpTo(TripsRoute) } },
                onTrips = { nav.popBackStack(TripsRoute, inclusive = false) },
                onCreateAccount = { nav.open(AccountRoute) },
            )
        }
        composable<NewTripRoute> {
            NewTripScreen(
                viewModel { NewTripViewModel(graph) },
                onClose = { nav.back() },
                onCreated = { id -> nav.open(TripRoute(id)) { popUpTo(TripsRoute) } },
            )
        }
        composable<TripRoute> { entry ->
            val id = entry.toRoute<TripRoute>().id
            TripScreen(
                viewModel { TripViewModel(graph, id) },
                viewModel { WishlistViewModel(graph, id) },
                viewModel { TripLogViewModel(graph, id) },
                viewModel { PlanViewModel(graph, id) },
                viewModel { GearViewModel(graph, id) },
                viewModel { ConflictViewModel(graph, id) },
                onBack = { nav.back() },
                onAddObjective = { nav.open(ObjectiveFormRoute(id)) },
                onOpenObjective = { nav.open(ObjectiveRoute(it)) },
                onEditDay = { nav.open(EditDayRoute(id, it)) },
                onSettings = { nav.open(TripSettingsRoute(id)) },
                onCrew = { nav.open(CrewRoute(id)) },
                onInvite = { nav.open(CrewRoute(id, invite = true)) },
            )
        }
        composable<ObjectiveFormRoute> { entry ->
            val route = entry.toRoute<ObjectiveFormRoute>()
            ObjectiveFormScreen(
                viewModel { ObjectiveFormViewModel(graph, route.tripId, route.objectiveId) },
                onClose = { nav.back() },
                onSaved = { id ->
                    // A new objective opens its detail so beta can be added right away (flow 4); an edit just closes.
                    if (route.objectiveId != null) nav.back()
                    else nav.open(ObjectiveRoute(id)) { popUpTo<ObjectiveFormRoute> { inclusive = true } }
                },
            )
        }
        composable<ObjectiveRoute> { entry ->
            val id = entry.toRoute<ObjectiveRoute>().id
            val vm = viewModel { ObjectiveViewModel(graph, id) }
            ObjectiveScreen(
                vm,
                onBack = { nav.back() },
                onEdit = { vm.state.value?.let { nav.open(ObjectiveFormRoute(it.objective.tripId, id)) } },
            )
        }
        composable<EditDayRoute> { entry ->
            val route = entry.toRoute<EditDayRoute>()
            EditDayScreen(viewModel { EditDayViewModel(graph, route.tripId, route.dayId) }, onClose = { nav.back() })
        }
        composable<LogbookRoute> {
            LogbookScreen(
                viewModel { LogbookViewModel(graph) },
                onBack = { nav.back() },
                onLogSend = { nav.open(LogSendRoute) },
                onOpen = { nav.open(ObjectiveRoute(it)) },
                onSettings = { nav.open(SettingsRoute) },
            )
        }
        composable<LogSendRoute> { LogSendScreen(viewModel { LogSendViewModel(graph) }, onClose = { nav.back() }) }
        composable<SettingsRoute> {
            SettingsScreen(viewModel { SettingsViewModel(graph) }, onBack = { nav.back() }, onCreateAccount = { nav.open(AccountRoute) })
        }
        // Back to whatever asked for an account: that screen carries on now that there is one (tech spec §2).
        composable<AccountRoute> { AccountScreen(viewModel { AccountViewModel(graph) }, onBack = { nav.back() }, onDone = { nav.back() }) }
        composable<TripSettingsRoute> { entry ->
            val tripId = entry.toRoute<TripSettingsRoute>().tripId
            TripSettingsScreen(
                viewModel { TripSettingsViewModel(graph, tripId) },
                onBack = { nav.back() },
                onDeleted = { nav.back(TripsRoute, inclusive = false) },
            )
        }
        composable<RecapRoute> { entry ->
            val tripId = entry.toRoute<RecapRoute>().tripId
            RecapScreen(viewModel { RecapViewModel(graph, tripId) }, onBack = { nav.back() })
        }
    }
}

/*
 * Every move goes through these, and only from a settled screen. A second tap on Back, or Back while a
 * transition runs, would otherwise pop twice; popping the start screen empties the back stack and leaves
 * a blank grey window. A double tap on a link would open the screen twice.
 */
private fun NavController.settled() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavController.open(route: Any, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (settled()) navigate(route, builder)
}

private fun NavController.back() {
    if (settled() && previousBackStackEntry != null) popBackStack()
}

private fun NavController.back(route: Any, inclusive: Boolean) {
    if (settled()) popBackStack(route, inclusive)
}
