plugins {
    alias(libs.plugins.com.eblan.launcher.libraryCompose)
}

android {
    namespace = "com.lionel.hub"
}

dependencies {
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.activity.compose)
}
