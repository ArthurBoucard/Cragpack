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
import app.cragpack.feature.newtrip.NewTripScreen
import app.cragpack.feature.newtrip.NewTripViewModel
import app.cragpack.feature.trip.TripScreen
import app.cragpack.feature.trip.TripViewModel
import app.cragpack.feature.trips.TripsScreen
import app.cragpack.feature.trips.TripsViewModel
import kotlinx.serialization.Serializable

@Serializable private data object TripsRoute
@Serializable private data object NewTripRoute
@Serializable private data class TripRoute(val id: String)
@Serializable private data object CatalogRoute

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
            TripScreen(viewModel { TripViewModel(graph, id) }, onBack = { nav.popBackStack() })
        }
        composable<CatalogRoute> { Catalog() }
    }
}
