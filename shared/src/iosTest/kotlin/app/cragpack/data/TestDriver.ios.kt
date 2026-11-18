package app.cragpack.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import app.cragpack.data.db.CragpackDb

actual fun testDriver(): SqlDriver = inMemoryDriver(CragpackDb.Schema)
