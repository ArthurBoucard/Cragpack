package app.cragpack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.ThemeMode
import app.cragpack.domain.settings.Theme
import app.cragpack.feature.LocalGradeDisplay
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
import app.cragpack.feature.wishlist.WishlistViewModel
import kotlinx.serialization.Serializable

@Serializable private data object TripsRoute
@Serializable private data object NewTripRoute
@Serializable private data class TripRoute(val id: String)
@Serializable private data object SettingsRoute
@Serializable private data class RecapRoute(val tripId: String)
@Serializable private data class ObjectiveFormRoute(val tripId: String, val objectiveId: String? = null)
@Serializable private data class ObjectiveRoute(val id: String)
@Serializable private data object LogbookRoute
@Serializable private data class EditDayRoute(val tripId: String, val dayId: String)
@Serializable private data object LogSendRoute

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
    CragpackTheme(mode) {
        CompositionLocalProvider(LocalGradeDisplay provides settings.gradeDisplay) { AppNav(graph) }
    }
}

@Composable
private fun AppNav(graph: AppGraph) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = TripsRoute) {
        composable<TripsRoute> {
            TripsScreen(
                viewModel { TripsViewModel(graph) },
                onNewTrip = { nav.navigate(NewTripRoute) },
                onOpenTrip = { nav.navigate(TripRoute(it)) },
                onRecap = { nav.navigate(RecapRoute(it)) },
                onLogbook = { nav.navigate(LogbookRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<NewTripRoute> {
            NewTripScreen(
                viewModel { NewTripViewModel(graph) },
                onClose = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(TripRoute(id)) { popUpTo(TripsRoute) } },
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
                onBack = { nav.popBackStack() },
                onAddObjective = { nav.navigate(ObjectiveFormRoute(id)) },
                onOpenObjective = { nav.navigate(ObjectiveRoute(it)) },
                onEditDay = { nav.navigate(EditDayRoute(id, it)) },
            )
        }
        composable<ObjectiveFormRoute> { entry ->
            val route = entry.toRoute<ObjectiveFormRoute>()
            ObjectiveFormScreen(
                viewModel { ObjectiveFormViewModel(graph, route.tripId, route.objectiveId) },
                onClose = { nav.popBackStack() },
                onSaved = { id ->
                    // A new objective opens its detail so beta can be added right away (flow 4); an edit just closes.
                    if (route.objectiveId != null) nav.popBackStack()
                    else nav.navigate(ObjectiveRoute(id)) { popUpTo<ObjectiveFormRoute> { inclusive = true } }
                },
            )
        }
        composable<ObjectiveRoute> { entry ->
            val id = entry.toRoute<ObjectiveRoute>().id
            val vm = viewModel { ObjectiveViewModel(graph, id) }
            ObjectiveScreen(
                vm,
                onBack = { nav.popBackStack() },
                onEdit = { vm.state.value?.let { nav.navigate(ObjectiveFormRoute(it.objective.tripId, id)) } },
            )
        }
        composable<EditDayRoute> { entry ->
            val route = entry.toRoute<EditDayRoute>()
            EditDayScreen(viewModel { EditDayViewModel(graph, route.tripId, route.dayId) }, onClose = { nav.popBackStack() })
        }
        composable<LogbookRoute> {
            LogbookScreen(
                viewModel { LogbookViewModel(graph) },
                onBack = { nav.popBackStack() },
                onLogSend = { nav.navigate(LogSendRoute) },
                onOpen = { nav.navigate(ObjectiveRoute(it)) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<LogSendRoute> { LogSendScreen(viewModel { LogSendViewModel(graph) }, onClose = { nav.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(viewModel { SettingsViewModel(graph) }, onBack = { nav.popBackStack() }) }
        composable<RecapRoute> { entry ->
            val tripId = entry.toRoute<RecapRoute>().tripId
            RecapScreen(viewModel { RecapViewModel(graph, tripId) }, onBack = { nav.popBackStack() })
        }
    }
}
