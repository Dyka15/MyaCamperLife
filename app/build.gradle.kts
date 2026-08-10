// Importato invece che scritto per esteso: in un build script `java` e' la
// property dell'estensione Gradle, non il nome del package, e `java.util.*`
// non si risolve.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Il commit da cui questo APK e' stato costruito.
 *
 * Serve alla nota di versione: `versionName` cambia una volta ogni tante fasi,
 * mentre gli APK si susseguono a ogni push, e «che build ho installato?» e' la
 * prima domanda davanti a un difetto. Il commit la risponde con certezza.
 *
 * Se git non c'e' — un sorgente scaricato come zip — resta "sviluppo": una nota
 * senza commit e' meno utile, ma una compilazione che fallisce per questo
 * sarebbe assurda.
 */
val commit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeUnless { it.isEmpty() } ?: "sviluppo"

/**
 * La chiave con cui si firma la release, se questa macchina ne ha una.
 *
 * Due sorgenti, nell'ordine: un `keystore.properties` nella radice del progetto
 * — comodo su una macchina di casa — e le variabili d'ambiente, che sono la via
 * della CI, dove i segreti arrivano da GitHub e non da un file.
 *
 * Il file non e' versionato e non deve esserlo: contiene le password
 * dell'archivio di chiavi. Il `.gitignore` lo esclude assieme ai `.jks`.
 *
 * Se la chiave non c'e', la compilazione **non** fallisce: l'APK di release
 * esce non firmato. Serve a verificare che R8 regga anche dove i segreti non
 * arrivano — un fork, un clone, una pull request.
 */
val proprietaFirma: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { sorgente ->
        Properties().apply { sorgente.inputStream().use { flusso -> load(flusso) } }
    }

fun segretoDiFirma(proprieta: String, ambiente: String): String? =
    (proprietaFirma?.getProperty(proprieta) ?: System.getenv(ambiente))
        ?.takeUnless { it.isBlank() }

android {
    namespace = "it.myacamperlife.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.myacamperlife.app"
        minSdk = 33          // Android 13
        targetSdk = 35       // Android 15
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "COMMIT", "\"$commit\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("it", "en")
    }

    // La chiave si dichiara solo se c'e' tutta: un archivio senza password, o
    // una password senza alias, farebbe fallire la compilazione con un errore
    // che parla di Gradle invece di dire cosa manca.
    val archivioChiavi = segretoDiFirma("archivio", "MYA_KEYSTORE")
        ?.let { percorso -> file(percorso) }
        ?.takeIf { it.isFile }
    val passwordArchivio = segretoDiFirma("passwordArchivio", "MYA_KEYSTORE_PASSWORD")
    val aliasChiave = segretoDiFirma("alias", "MYA_KEY_ALIAS")
    val passwordChiave = segretoDiFirma("passwordChiave", "MYA_KEY_PASSWORD")

    if (archivioChiavi != null && passwordArchivio != null &&
        aliasChiave != null && passwordChiave != null
    ) {
        signingConfigs.create("rilascio") {
            storeFile = archivioChiavi
            storePassword = passwordArchivio
            keyAlias = aliasChiave
            keyPassword = passwordChiave
        }
    }

    buildTypes {
        release {
            // R8 accorcia e offusca. Le regole che tengono in piedi le parti
            // raggiunte per riflessione stanno in proguard-rules.pro, dove ogni
            // `-keep` dice perche' esiste.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // `null` quando la chiave non c'e': l'APK esce non firmato e il
            // nome del file lo dice — `app-release-unsigned.apk`.
            signingConfig = signingConfigs.findByName("rilascio")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Il guardiano del briefing: un controllo ogni sei ore che la sveglia
    // delle 19:00 sia ancora in coda. WorkManager sopravvive dove HyperOS
    // pota le sveglie.
    implementation(libs.androidx.work.runtime.ktx)

    // Lo specchio nella cartella scelta dall'utente: attraversare un albero
    // SAF a mano e' cento righe di DocumentsContract, questa libreria pesa
    // trenta kilobyte.
    implementation(libs.androidx.documentfile)

    // Le chiavi API dei modelli, cifrate col Keystore. Non possono stare
    // in impostazioni.json: quel file viene rispecchiato su un cloud.
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
