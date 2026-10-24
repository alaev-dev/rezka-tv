plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

val python = providers.environmentVariable("PYTHON").getOrElse("python3")
val gateBase = providers.environmentVariable("GATE_BASE").getOrElse("origin/main")

val ruff by tasks.registering(Exec::class) {
    commandLine(python, "-m", "ruff", "check", "tools")
}

val toolsTests by tasks.registering(Exec::class) {
    commandLine(python, "-m", "unittest", "discover", "-s", "tools", "-q")
}

val prSize by tasks.registering(Exec::class) {
    commandLine(python, "tools/gates/pr_size.py", gateBase)
}

tasks.register("verify") {
    group = "verification"
    description = "Быстрый эшелон проверок: одна команда для CI и для любой машины."
    dependsOn(ruff, toolsTests, prSize, ":app:testDebugUnitTest", ":app:lintDebug")
}
