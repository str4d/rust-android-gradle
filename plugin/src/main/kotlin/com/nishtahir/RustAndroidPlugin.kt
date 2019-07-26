package com.nishtahir

import com.android.build.gradle.*
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Properties

const val RUST_TASK_GROUP = "rust"

enum class ToolchainType {
    ANDROID_PREBUILT,
    ANDROID_GENERATED,
    DESKTOP,
}

// See https://forge.rust-lang.org/platform-support.html.
val toolchains = listOf(
        Toolchain("linux-x86-64",
                ToolchainType.DESKTOP,
                "x86_64-unknown-linux-gnu",
                "<compilerTriple>",
                "<binutilsTriple>",
                0,
                "desktop/linux-x86-64"),
        Toolchain("darwin",
                ToolchainType.DESKTOP,
                "x86_64-apple-darwin",
                "<compilerTriple>",
                "<binutilsTriple>",
                0,
                "desktop/darwin"),
        Toolchain("win32-x86-64-msvc",
                ToolchainType.DESKTOP,
                "x86_64-pc-windows-msvc",
                "<compilerTriple>",
                "<binutilsTriple>",
                0,
                "desktop/win32-x86-64"),
        Toolchain("win32-x86-64-gnu",
                ToolchainType.DESKTOP,
                "x86_64-pc-windows-gnu",
                "<compilerTriple>",
                "<binutilsTriple>",
                0,
                "desktop/win32-x86-64"),
        Toolchain("arm",
                ToolchainType.ANDROID_GENERATED,
                "armv7-linux-androideabi",
                "arm-linux-androideabi",
                "arm-linux-androideabi",
                16,
                "android/armeabi-v7a"),
        Toolchain("arm64",
                ToolchainType.ANDROID_GENERATED,
                "aarch64-linux-android",
                "aarch64-linux-android",
                "aarch64-linux-android",
                21,
                "android/arm64-v8a"),
        Toolchain("x86",
                ToolchainType.ANDROID_GENERATED,
                "i686-linux-android",
                "i686-linux-android",
                "i686-linux-android",
                16,
                "android/x86"),
        Toolchain("x86_64",
                ToolchainType.ANDROID_GENERATED,
                "x86_64-linux-android",
                "x86_64-linux-android",
                "x86_64-linux-android",
                21,
                "android/x86_64"),
        Toolchain("arm",
                ToolchainType.ANDROID_PREBUILT,
                "armv7-linux-androideabi",
                "armv7a-linux-androideabi",
                "arm-linux-androideabi",
                16,
                "android/armeabi-v7a"),
        Toolchain("arm64",
                ToolchainType.ANDROID_PREBUILT,
                "aarch64-linux-android",
                "aarch64-linux-android",
                "aarch64-linux-android",
                21,
                "android/arm64-v8a"),
        Toolchain("x86",
                ToolchainType.ANDROID_PREBUILT,
                "i686-linux-android",
                "i686-linux-android",
                "i686-linux-android",
                16,
                "android/x86"),
        Toolchain("x86_64",
                ToolchainType.ANDROID_PREBUILT,
                "x86_64-linux-android",
                "x86_64-linux-android",
                "x86_64-linux-android",
                21,
                "android/x86_64")
)

data class Toolchain(val platform: String,
                     val type: ToolchainType,
                     val target: String,
                     val compilerTriple: String,
                     val binutilsTriple: String,
                     val minApiLevel: Int,
                     val folder: String) {
    fun apiLevel(desiredApiLevel: Int, forceArchs: Boolean): Int =
            if (desiredApiLevel < minApiLevel) {
                if (forceArchs) {
                    println("Can't target ${platform} with API level < ${minApiLevel} (${desiredApiLevel})")
                    println("Forcing API level to ${minApiLevel}")
                    minApiLevel
                } else {
                    throw GradleException("Can't target ${platform} with API level < ${minApiLevel} (${desiredApiLevel})")
                }
            } else {
                desiredApiLevel
            }

    fun cc(apiLevel: Int): File =
            if (System.getProperty("os.name").startsWith("Windows")) {
                if (type == ToolchainType.ANDROID_PREBUILT) {
                    File("bin", "$compilerTriple$apiLevel-clang.cmd")
                } else {
                    File("$platform-$apiLevel/bin", "$compilerTriple-clang.cmd")
                }
            } else {
                if (type == ToolchainType.ANDROID_PREBUILT) {
                    File("bin", "$compilerTriple$apiLevel-clang")
                } else {
                    File("$platform-$apiLevel/bin", "$compilerTriple-clang")
                }
            }

    fun ar(apiLevel: Int): File =
            if (type == ToolchainType.ANDROID_PREBUILT) {
                File("bin", "$binutilsTriple-ar")
            } else {
                File("$platform-$apiLevel/bin", "$binutilsTriple-ar")
            }
}

@Suppress("unused")
open class RustAndroidPlugin : Plugin<Project> {
    internal lateinit var cargoExtension: CargoExtension

    override fun apply(project: Project) {
        with(project) {
            cargoExtension = extensions.create("cargo", CargoExtension::class.java)
            cargoExtension.variants = project.container(CargoOverrides::class.java)

            afterEvaluate {
                plugins.all {
                    when (it) {
                        is AppPlugin -> configurePlugin<AppExtension, ApplicationVariant>(
                            this, extensions[AppExtension::class].applicationVariants)
                        is LibraryPlugin -> configurePlugin<LibraryExtension, LibraryVariant>(
                            this, extensions[LibraryExtension::class].libraryVariants)
                    }
                }
            }

        }
    }

    private inline fun <reified T : BaseExtension, reified V : BaseVariant> configurePlugin(
        project: Project, variants: DomainObjectSet<V>
    ) = with(project) {
        cargoExtension.localProperties = Properties()

        val localPropertiesFile = File(project.rootDir, "local.properties")
        if (localPropertiesFile.exists()) {
            cargoExtension.localProperties.load(localPropertiesFile.inputStream())
        }

        if (cargoExtension.module == null) {
            throw GradleException("module cannot be null")
        }

        if (cargoExtension.libname == null) {
            throw GradleException("libname cannot be null")
        }

        // Allow to set targets, including per-project, in local.properties.
        val localTargets: String? =
                cargoExtension.localProperties.getProperty("rust.targets.${project.name}") ?:
                cargoExtension.localProperties.getProperty("rust.targets")
        if (localTargets != null) {
            cargoExtension.targets = localTargets.split(',').map { it.trim() }
        }

        if (cargoExtension.targets == null) {
            throw GradleException("targets cannot be null")
        }

        // Determine the NDK version, if present
        val ndkSourceProperties = Properties()
        val ndkSourcePropertiesFile = File(extensions[T::class].ndkDirectory, "source.properties")
        if (ndkSourcePropertiesFile.exists()) {
            ndkSourceProperties.load(ndkSourcePropertiesFile.inputStream())
        }
        val ndkVersion = ndkSourceProperties.getProperty("Pkg.Revision", "0.0")
        val ndkVersionMajor = ndkVersion.split(".").first().toInt()

        // Determine whether to use prebuilt or generated toolchains
        val usePrebuilt = if (cargoExtension.prebuiltToolchains == null) {
            ndkVersionMajor >= 19
        } else {
            cargoExtension.prebuiltToolchains!!
        }
        if (usePrebuilt && ndkVersionMajor < 19) {
            throw GradleException("usePrebuilt = true requires NDK version 19+")
        }

        val generateToolchain = if (!usePrebuilt) {
            tasks.maybeCreate("generateToolchains",
                    GenerateToolchainsTask::class.java).apply {
                group = RUST_TASK_GROUP
                description = "Generate standard toolchain for given architectures"
            }
        } else {
            null
        }

        // Fish linker wrapper scripts from our Java resources.
        val generateLinkerWrapper = rootProject.tasks.maybeCreate("generateLinkerWrapper", GenerateLinkerWrapperTask::class.java).apply {
            group = RUST_TASK_GROUP
            description = "Generate shared linker wrapper script"
        }

        generateLinkerWrapper.apply {
            // From https://stackoverflow.com/a/320595.
            from(rootProject.zipTree(File(RustAndroidPlugin::class.java.protectionDomain.codeSource.location.toURI()).path))
            include("**/linker-wrapper*")
            into(File(rootProject.buildDir, "linker-wrapper"))
            eachFile {
                it.path = it.path.replaceFirst("com/nishtahir", "")
            }
            fileMode = 493 // 0755 in decimal; Kotlin doesn't have octal literals (!).
            includeEmptyDirs = false
        }

        // Configure builds for customized variants
        var runDefaultBuild = false
        variants.all { variant ->
            if (cargoExtension.hasOverrides(variant.name)) {
                if (cargoExtension.isEnabled(variant.name)) {
                    configureBuild<T, V>(
                        project,
                        variants,
                        generateToolchain,
                        generateLinkerWrapper,
                        variant)
                }
            } else {
                runDefaultBuild = true
            }
        }

        // Configure the default build if necessary
        if (runDefaultBuild) {
            configureBuild<T, V>(
                project,
                variants,
                generateToolchain,
                generateLinkerWrapper,
                null)
        }
    }

    private inline fun <reified T : BaseExtension, reified V : BaseVariant> configureBuild(
        project: Project,
        variants: DomainObjectSet<V>,
        generateToolchain: GenerateToolchainsTask?,
        generateLinkerWrapper: GenerateLinkerWrapperTask,
        variant: V?
    ) = with(project) {
        val variantNameCap = variant?.name?.capitalize() ?: ""
        val variantDir = if (variant != null) "/${variant.dirName}" else "/"

        extensions[T::class].apply {
            sourceSets.getByName(variant?.name ?: "main").jniLibs.srcDir(File("$buildDir/rustJniLibs${variantDir}/android"))
            sourceSets.getByName("test${variantNameCap}").resources.srcDir(File("$buildDir/rustJniLibs${variantDir}/desktop"))
        }

        val buildTask = tasks.maybeCreate("cargoBuild${variantNameCap}",
                DefaultTask::class.java).apply {
            group = RUST_TASK_GROUP
            description = "Build library (all targets)"
        }

        cargoExtension.targets!!.forEach { target ->
            val theToolchain = toolchains
                    .filter {
                        if (generateToolchain == null) {
                            it.type != ToolchainType.ANDROID_GENERATED
                        } else {
                            it.type != ToolchainType.ANDROID_PREBUILT
                        }
                    }
                    .find { it.platform == target }
            if (theToolchain == null) {
                throw GradleException("Target ${target} is not recognized (recognized targets: ${toolchains.map { it.platform }.sorted()}).  Check `local.properties` and `build.gradle`.")
            }

            val targetBuildTask = tasks.maybeCreate("cargoBuild${variantNameCap}For${target.capitalize()}",
                    CargoBuildTask::class.java).apply {
                group = RUST_TASK_GROUP
                description = "Build library ($target)"
                toolchain = theToolchain
                variantDirectory = variantDir
            }

            if (generateToolchain != null) {
                targetBuildTask.dependsOn(generateToolchain)
            }
            targetBuildTask.dependsOn(generateLinkerWrapper)
            buildTask.dependsOn(targetBuildTask)
        }

        if (variant != null) {
            // This is a specific variant
            tasks.getByPath("generate${variantNameCap}Assets").dependsOn(buildTask)
        } else {
            // This is the generic build for uncustomized variants
            variants.all {
                if (!cargoExtension.hasOverrides(it.name)) {
                    tasks.getByPath("generate${it.name.capitalize()}Assets").dependsOn(buildTask)
                }
            }
        }
    }
}
