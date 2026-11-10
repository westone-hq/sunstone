// 앱 연결 모듈. Pleos SDK·화면·음성·Gleo·데모 입력을 routines 모듈의 루틴에 이어 붙인다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.westone.sunstone"
    compileSdk = 35
    // 차량 속도(PERF_VEHICLE_SPEED)를 AAOS Car API로 읽는다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.westone.sunstone"
        minSdk = 28 // Pleos 요구사항(사이닝 키 로테이션)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":routines"))
    implementation(libs.vehicle.sdk)
    // Pleos 내비(경로·경유지·남은 거리·도착). 지금은 debug 실측(`navi`)만 쓴다.
    implementation(libs.navi.helper)
    // Pleos 위치(현재 위치·근접 알림). 지금은 debug 실측(`location`)만 쓴다.
    implementation(libs.fused.location)
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
}
