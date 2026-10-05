/*
 *
 *   Copyright 2023 Einstein Blanco
 *
 *   Licensed under the GNU General Public License v3.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.gnu.org/licenses/gpl-3.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
plugins {
    alias(libs.plugins.com.eblan.launcher.application)
    alias(libs.plugins.com.eblan.launcher.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.eblan.launcher"

    defaultConfig {
        applicationId = "com.lionel.launcher"
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI passes a stable keystore via these variables so updates install over the old version.
    // Without them (local builds) the release build is signed with the debug key.
    val lionelKeystore = System.getenv("LIONEL_KEYSTORE")
    signingConfigs {
        if (lionelKeystore != null) {
            create("lionel") {
                storeFile = file(lionelKeystore)
                storePassword = System.getenv("LIONEL_STORE_PASSWORD")
                keyAlias = System.getenv("LIONEL_KEY_ALIAS")
                keyPassword = System.getenv("LIONEL_KEY_PASSWORD")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            signingConfig = signingConfigs.getByName(if (lionelKeystore != null) "lionel" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.common)
    implementation(projects.data.repository)
    implementation(projects.designSystem)
    implementation(projects.domain.framework)
    implementation(projects.domain.model)
    implementation(projects.domain.useCase)
    implementation(projects.domain.repository)
    implementation(projects.feature.action)
    implementation(projects.feature.editApplicationInfo)
    implementation(projects.feature.editFolderApplicationInfo)
    implementation(projects.feature.editGridItem)
    implementation(projects.feature.home)
    implementation(projects.feature.pin)
    implementation(projects.feature.settings.appDrawer)
    implementation(projects.feature.settings.experimental)
    implementation(projects.feature.settings.folder)
    implementation(projects.feature.settings.general)
    implementation(projects.feature.settings.gestures)
    implementation(projects.feature.settings.home)
    implementation(projects.feature.settings.settings)
    implementation(projects.framework.contentResolver)
    implementation(projects.framework.jaroWinklerSimilarity)
    implementation(projects.framework.notificationManager)
    implementation(projects.framework.resources)
    implementation(projects.service)
    implementation(projects.ui)
    implementation(projects.lionel)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
}