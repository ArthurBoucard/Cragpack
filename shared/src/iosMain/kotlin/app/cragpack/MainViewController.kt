package app.cragpack

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cragpack.data.AppGraph
import app.cragpack.data.db.CragpackDb

private val graph by lazy { AppGraph(NativeSqliteDriver(CragpackDb.Schema, "cragpack.db")).also { it.startSync() } }

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController() = ComposeUIViewController { App(graph) }
