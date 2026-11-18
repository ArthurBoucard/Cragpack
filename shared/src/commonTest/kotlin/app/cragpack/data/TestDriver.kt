package app.cragpack.data

import app.cash.sqldelight.db.SqlDriver

/** A fresh in-memory database with the schema applied. */
expect fun testDriver(): SqlDriver
