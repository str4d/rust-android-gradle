package com.nishtahir

import java.io.File

import com.android.build.gradle.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

open class GenerateToolchainsTask : DefaultTask() {

    @TaskAction
    @Suppress("unused")
    fun generateToolchainTask() {
        project.plugins.all {
            when (it) {
                is AppPlugin -> configureTask<AppExtension>(project)
                is LibraryPlugin -> configureTask<LibraryExtension>(project)
            }
        }
    }

    inline fun <reified T : BaseExtension> configureTask(project: Project) {
        val cargoExtension = project.extensions[CargoExtension::class]
        val app = project.extensions[T::class]
        val desiredApiLevel = cargoExtension.apiLevel ?: app.defaultConfig.minSdkVersion.apiLevel
        val ndkPath = app.ndkDirectory

        // It's safe to unwrap, since we bailed at configuration time if this is unset.
        val targets = cargoExtension.targets!!

        toolchains
                .filter { it.type == ToolchainType.ANDROID_GENERATED }
                .filter { (arch) -> targets.contains(arch) }
                .forEach { toolchain ->
                    val arch = toolchain.platform
                    val apiLevel = toolchain.apiLevel(desiredApiLevel, cargoExtension.forceTargets)

                    val dir = File(cargoExtension.toolchainDirectory, arch + "-" + apiLevel)
                    if (dir.exists()) {
                        println("Toolchain for arch ${arch} version ${apiLevel} exists: checked ${dir}")
                        return@forEach
                    }

                    println("Toolchain for arch ${arch} version ${apiLevel} does not exist: checked ${dir}")
                    project.exec { spec ->
                        spec.standardOutput = System.out
                        spec.errorOutput = System.out
                        spec.commandLine(cargoExtension.pythonCommand)
                        spec.args("$ndkPath/build/tools/make_standalone_toolchain.py",
                                  "--arch=$arch",
                                  "--api=$apiLevel",
                                  "--install-dir=${dir}",
                                  "--force")
                    }
                }
    }
}
