package com.nishtahir

open class CargoExtension {
    var module: String = ""
    var targets: List<String> = emptyList()

    /**
     * The Cargo [release profile](https://doc.rust-lang.org/book/second-edition/ch14-01-release-profiles.html#customizing-builds-with-release-profiles) to build.
     *
     * Defaults to `"debug"`.
     */
    var profile: String = "debug"

    /**
     * The target directory into Cargo which writes built outputs.
     *
     * Defaults to `${module}/target`.
     */
    var targetDirectory: String? = null

    /**
     * Which Cargo built outputs to consider JNI libraries.
     *
     * Defaults to `["*.so", "*.dylib", "*.dll"]`.
     */
    var targetIncludes: Array<String> = arrayOf("*.so", "*.dylib", "*.dll")

    /**
     * Android toolchains know where to put their outputs; it's a well-known value like
     * `armeabi-v7a` or `x86`.  The default toolchain outputs don't know where to put their output;
     * use this to say where.
     *
     * Defaults to `""`.
     */
    var defaultToolchainBuildPrefixDir: String? = ""
}
