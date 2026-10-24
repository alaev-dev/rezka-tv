plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

val python = providers.environmentVariable("PYTHON").getOrElse("python3")
val gateBase = providers.environmentVariable("GATE_BASE").getOrElse("origin/main")

val ruff by tasks.registering(Exec::class) {
    commandLine(python, "-m", "ruff", "check", "tools")
}

val ruffFormat by tasks.registering(Exec::class) {
    commandLine(python, "-m", "ruff", "format", "--check", "tools")
}

val typos by tasks.registering(Exec::class) {
    commandLine(providers.environmentVariable("TYPOS").getOrElse("typos"), "--format", "brief")
}

val layoutGate by tasks.registering(Exec::class) {
    commandLine(python, "tools/gates/layout.py")
}

val testRules by tasks.registering(Exec::class) {
    commandLine(python, "tools/gates/discipline.py")
}

val toolsTests by tasks.registering(Exec::class) {
    commandLine(python, "-m", "unittest", "discover", "-s", "tools", "-q")
}

val prSize by tasks.registering(Exec::class) {
    commandLine(python, "tools/gates/pr_size.py", gateBase)
}

val commentsGate by tasks.registering(Exec::class) {
    commandLine(python, "tools/gates/comments.py", gateBase)
}

tasks.register("verify") {
    group = "verification"
    description = "Быстрый эшелон проверок: одна команда для CI и для любой машины."
    dependsOn(ruff, ruffFormat, typos, toolsTests, prSize, commentsGate, layoutGate, testRules, ":app:testDebugUnitTest", ":app:lintDebug", ":app:detekt")
}
