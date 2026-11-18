package app.cragpack

import androidx.compose.runtime.Composable
import app.cragpack.designsystem.Catalog

/** Root of the app on both platforms. Phase 0 shows the design-system catalog; Phase 1 replaces it with Trips. */
@Composable
fun App() = Catalog()
