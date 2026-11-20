import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("CragpackDb") {
            packageName.set("app.cragpack.data.db")
        }
    }
}

kotlin {
    android {
        namespace = "app.cragpack.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.designsystem)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.reorderable)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}

// design/data/grades.json -> GradeTable.kt: the grade table stays the single source (tech spec §6).
val generateGradeTable = tasks.register("generateGradeTable") {
    val input = rootProject.layout.projectDirectory.file("design/data/grades.json")
    val output = layout.buildDirectory.dir("generated/grades")
    inputs.file(input)
    outputs.dir(output)
    doLast {
        val json = groovy.json.JsonSlurper().parse(input.asFile) as Map<*, *>
        fun table(key: String, valueKey: String, equivalentKey: String?) =
            (json[key] as List<*>).joinToString(",\n", "linkedMapOf(\n", ",\n    )") {
                val row = it as Map<*, *>
                val eq = equivalentKey?.let { k -> "\"${row[k]}\"" } ?: "null"
                "        \"${row[valueKey]}\" to GradeRow(band = ${row["band"]}, index = ${row["index"]}, equivalent = $eq)"
            }
        val file = output.get().file("app/cragpack/domain/grade/GradeTable.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from design/data/grades.json by :shared:generateGradeTable. Do not edit.
            |package app.cragpack.domain.grade
            |
            |internal object GradeTable {
            |    val font = ${table("boulder_font_to_v", "font", "v")}
            |    val v = ${table("boulder_v", "v", "font")}
            |    val french = ${table("route_french", "french", null)}
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateGradeTable) }

// local.properties -> SupabaseConfig.kt. Without keys (CI, a fresh clone) the app stays on this phone.
val generateSupabaseConfig = tasks.register("generateSupabaseConfig") {
    val props = rootProject.layout.projectDirectory.file("local.properties").asFile
    val values = Properties().apply { if (props.exists()) props.inputStream().use(::load) }
        .let { p -> listOf("supabase.url", "supabase.key").map { p.getProperty(it).orEmpty().trim() } }
    val output = layout.buildDirectory.dir("generated/supabase")
    inputs.property("values", values)
    outputs.dir(output)
    doLast {
        val file = output.get().file("app/cragpack/data/SupabaseConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from local.properties by :shared:generateSupabaseConfig. Do not edit.
            |package app.cragpack.data
            |
            |internal object SupabaseConfig {
            |    const val URL = "${values[0]}"
            |    const val KEY = "${values[1]}"
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateSupabaseConfig) }
