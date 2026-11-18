package app.cragpack

import android.app.Application
import app.cragpack.data.createAppGraph

class CragpackApp : Application() {
    val graph by lazy { createAppGraph(this) }
}
