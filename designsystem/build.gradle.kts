plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "app.cragpack.designsystem"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "app.cragpack.designsystem.generated.resources"
}

// design/tokens/cragpack.tokens.json -> Tokens.kt, so the theme never drifts from the design.
val generateTokens = tasks.register("generateTokens") {
    val input = rootProject.layout.projectDirectory.file("design/tokens/cragpack.tokens.json")
    val output = layout.buildDirectory.dir("generated/tokens")
    inputs.file(input)
    outputs.dir(output)
    doLast {
        @Suppress("UNCHECKED_CAST")
        fun Any?.obj() = (this as Map<String, Any?>).filterKeys { !it.startsWith("$") }
        fun Any?.value() = (this as Map<*, *>)["\$value"]
        fun color(hex: Any?): String {
            val h = (hex as String).removePrefix("#").uppercase()
            return "Color(0x" + (if (h.length == 8) h.substring(6) + h.substring(0, 6) else "FF$h") + ")"
        }
        fun dp(v: Any?) = (v as String).removeSuffix("px") + ".dp"
        fun float(v: Any?) = v.toString() + "f"

        val json = groovy.json.JsonSlurper().parse(input.asFile)
        val colors = json.obj()["color"].obj()
        fun scheme(name: String) = colors[name].obj().entries
            .joinToString(",\n", "internal val ${name.replaceFirstChar { it.uppercase() }}Colors = CragColors(\n", ",\n)") {
                "    ${it.key} = ${color(it.value.value())}"
            }
        val grade = json.obj()["grade"].obj()
        val gold = (grade["9"].value() as List<*>).joinToString { s ->
            val stop = s as Map<*, *>
            "${float(stop["position"])} to ${color(stop["color"])}"
        }
        fun dims(group: String) = json.obj()[group].obj().entries
            .filter { (it.value as Map<*, *>)["\$type"] == "dimension" }
            .joinToString("\n") { "    val ${it.key} = ${dp(it.value.value())}" }
        val type = json.obj()["type"].obj().entries.joinToString("\n") { (name, token) ->
            val v = token.value() as Map<*, *>
            val role = (v["fontFamily"] as String).removeSurrounding("{font.", "}").replaceFirstChar { it.uppercase() }
            val spacing = (v["letterSpacing"] as String?)?.removeSuffix("em") ?: "0"
            "    val $name = TypeToken(FontRole.$role, sizeSp = ${float((v["fontSize"] as String).removeSuffix("px"))}, " +
                "weight = ${v["fontWeight"]}, lineHeight = ${float(v["lineHeight"])}, letterSpacingEm = ${float(spacing)})"
        }

        val file = output.get().file("app/cragpack/designsystem/tokens/Tokens.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from design/tokens/cragpack.tokens.json by :designsystem:generateTokens. Do not edit.
            |package app.cragpack.designsystem.tokens
            |
            |import androidx.compose.ui.graphics.Color
            |import androidx.compose.ui.unit.dp
            |import app.cragpack.designsystem.theme.CragColors
            |import app.cragpack.designsystem.theme.FontRole
            |import app.cragpack.designsystem.theme.TypeToken
            |
            |${scheme("dark")}
            |
            |${scheme("light")}
            |
            |object ColorTokens {
            |    val photoScrim = ${color(colors["photoScrim"].value())}
            |    val overlayScrim = ${color(colors["overlayScrim"].value())}
            |    /** Grade band backgrounds; index 0 = band 1. Band 9 is [gradeGold]. */
            |    val gradeBands = listOf(${(1..8).joinToString { color(grade["$it"].value()) }})
            |    val gradeGold = listOf($gold)
            |    /** One colour per climber, in join order. */
            |    val people = listOf(${json.obj()["person"].obj().values.joinToString { color(it.value()) }})
            |}
            |
            |object SizeTokens {
            |${dims("size")}
            |}
            |
            |object RadiusTokens {
            |${dims("radius")}
            |}
            |
            |object IconTokens {
            |${dims("icon")}
            |}
            |
            |internal object TypeTokens {
            |$type
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateTokens) }
