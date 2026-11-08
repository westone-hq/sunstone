import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 기능 구현 모듈. 안드로이드·Pleos에 의존하지 않는다.
// 그래서 차 없이 JVM 단위 테스트로 루틴을 끝까지 돌려볼 수 있다.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
