// 앱 연결 모듈. Pleos SDK·화면·음성·Gleo·데모 입력을 routines 모듈의 루틴에 이어 붙인다.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// release 서명 값. 저장소에 올리지 않는 local.properties에서 읽는다(keystore도 저장소 밖에 둔다).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signingKeys = listOf("release.storeFile", "release.storePassword", "release.keyAlias", "release.keyPassword")
val missingSigning = signingKeys.filter { localProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.westone.sunstone"
    compileSdk = 35
    // 차량 속도(PERF_VEHICLE_SPEED)를 AAOS Car API로 읽는다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.westone.sunstone"
        minSdk = 28 // Pleos 요구사항(사이닝 키 로테이션)
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (missingSigning.isEmpty()) {
            create("release") {
                storeFile = file(localProps.getProperty("release.storeFile"))
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// 서명 값이 없으면 설치되지 않는 APK가 나오므로 release 묶기 전에 이유를 말하고 멈춘다.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (missingSigning.isNotEmpty()) {
            throw GradleException(
                "release 서명 값이 없습니다. local.properties에 ${missingSigning.joinToString()}를 넣으세요 " +
                    "(README '테스트 환경과 필요 설정' 참고).",
            )
        }
    }
}

// 공모전 규정 파일명으로 제출 APK를 apk/에 둔다.
val copyReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "westone_Sunstone_release.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }

dependencies {
    implementation(project(":routines"))
    implementation(libs.vehicle.sdk)
    // Pleos 내비(경로·경유지·남은 거리·도착).
    implementation(libs.navi.helper)
    // Pleos 위치(현재 위치·근접 알림).
    implementation(libs.fused.location)
    // Pleos 음성 합성(앱이 하는 말, ON_DEVICE).
    implementation(libs.pleos.tts)
    // Pleos 음성 인식. 지금은 debug 실측(`speech`)만 쓴다.
    debugImplementation(libs.pleos.stt)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
}

// 경로만 테스트에 전달한다. 재발급 키는 Gradle 필드·APK에 넣지 않는다.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("sunstone.localProperties", rootProject.file("local.properties").absolutePath)
}
