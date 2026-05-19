android {
    // ... other configurations ...applicationVariants.all {
    val variant = this
    variant.outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
        .forEach { output ->
            // Custom name format: Zendence_v1.0_debug.apk
            val newName = "Zendence_v${variant.versionName}_${variant.buildType.name}.apk"
            output.outputFileName = newName
        }
}
}