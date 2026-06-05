plugins {
    // 로컬에 JDK 21이 없으면 자동으로 다운로드 (Linux/macOS/Windows 공통)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CodeFabInterpreter"
