package app.cragpack.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cragpack.data.db.CragpackDb

fun createAppGraph(context: Context) = AppGraph(AndroidSqliteDriver(CragpackDb.Schema, context, "cragpack.db")).also { it.startSync() }
