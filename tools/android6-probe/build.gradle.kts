plugins { id("com.android.application") version "9.3.0" }
android {
    namespace = "com.shihab.diplay.android6probe"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.shihab.diplay.android6probe"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-source-probe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
// Exercise the actual application pump, with no common/UI/native/authentication dependency.
abstract class GenerateUsbPump : DefaultTask() {
    @get:InputDirectory abstract val sourceDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        for (name in listOf("UsbReadTransport.kt", "LegacyUsbReadPump.kt")) {
            val relative = "com/shilapi/xcertplay/transport/$name"
            val destination = outputDirectory.get().file(relative).asFile
            destination.parentFile.mkdirs()
            sourceDirectory.get().file(relative).asFile.copyTo(destination, overwrite = true)
        }
    }
}
val syncUsbPump = tasks.register<GenerateUsbPump>("syncUsbPump") {
    sourceDirectory.set(layout.projectDirectory.dir("../../shared/src/main/java"))
}
androidComponents.onVariants { variant ->
    variant.sources.kotlin?.addGeneratedSourceDirectory(syncUsbPump, GenerateUsbPump::outputDirectory)
}
