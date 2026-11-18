package app.cragpack

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.Catalog
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.feature.gear.GearViewModel
import app.cragpack.feature.logbook.LogSendScreen
import app.cragpack.feature.logbook.LogSendViewModel
import app.cragpack.feature.logbook.LogbookScreen
import app.cragpack.feature.logbook.LogbookViewModel
import app.cragpack.feature.newtrip.NewTripScreen
import app.cragpack.feature.plan.EditDayScreen
import app.cragpack.feature.plan.EditDayViewModel
import app.cragpack.feature.plan.PlanViewModel
import app.cragpack.feature.triplog.TripLogViewModel
import app.cragpack.feature.newtrip.NewTripViewModel
import app.cragpack.feature.objective.ObjectiveFormScreen
import app.cragpack.feature.objective.ObjectiveFormViewModel
import app.cragpack.feature.objective.ObjectiveScreen
import app.cragpack.feature.objective.ObjectiveViewModel
import app.cragpack.feature.wishlist.WishlistViewModel
import app.cragpack.feature.trip.TripScreen
import app.cragpack.feature.trip.TripViewModel
import app.cragpack.feature.trips.TripsScreen
import app.cragpack.feature.trips.TripsViewModel
import kotlinx.serialization.Serializable

@Serializable private data object TripsRoute
@Serializable private data object NewTripRoute
@Serializable private data class TripRoute(val id: String)
@Serializable private data object CatalogRoute
@Serializable private data class ObjectiveFormRoute(val tripId: String, val objectiveId: String? = null)
@Serializable private data class ObjectiveRoute(val id: String)
@Serializable private data object LogbookRoute
@Serializable private data class EditDayRoute(val tripId: String, val dayId: String)
@Serializable private data object LogSendRoute

/** Root of the app on both platforms. Trips home is the root (docs/03 navigation model). */
@Composable
fun App(graph: AppGraph) = CragpackTheme {
    val nav = rememberNavController()
    NavHost(nav, startDestination = TripsRoute) {
        composable<TripsRoute> {
            TripsScreen(
                viewModel { TripsViewModel(graph) },
                onNewTrip = { nav.navigate(NewTripRoute) },
                onOpenTrip = { nav.navigate(TripRoute(it)) },
                onLogbook = { nav.navigate(LogbookRoute) },
                // ponytail: ⋮ shows the design-system catalog until Settings exists.
                onMore = { nav.navigate(CatalogRoute) },
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
            )
        }
        composable<LogSendRoute> { LogSendScreen(viewModel { LogSendViewModel(graph) }, onClose = { nav.popBackStack() }) }
        composable<CatalogRoute> { Catalog() }
    }
}
