# Regole per R8, la release e' minificata.
#
# Ogni `-keep` qui dentro sta per qualcosa che non si vede nel codice: una
# classe raggiunta per riflessione, o per nome, o istanziata dal sistema. R8
# ragiona sulle chiamate, quindi tutto quello che non viene chiamato da nessuna
# riga scompare — ed e' il comportamento giusto, purche' gli si dica dove
# guardare oltre alle chiamate.
#
# Le classi dichiarate nel manifest (MainActivity, i due receiver, il
# FileProvider, MyaApplication) le tiene AGP da se': legge il manifest e le
# aggiunge alle radici. Non servono regole per loro.

# --- tracce leggibili ---------------------------------------------------------
# Senza queste due, una segnalazione di crash arriva con `Unknown Source` e non
# resta niente da capire. Il file di mappatura per rileggere i nomi offuscati
# finisce in app/build/outputs/mapping/release/.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ----------------------------------------------------
# Le classi @Serializable hanno un serializzatore generato dal compilatore, che
# in parte si raggiunge per nome. Sono le regole della documentazione di
# kotlinx.serialization, non un adattamento: le nostre @Serializable sono cinque
# (Meteo, l'impostazioni, il dossier), e ognuna e' un pezzo di archivio.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- WorkManager --------------------------------------------------------------
# I due Worker — il guardiano del briefing e lo specchio — vengono costruiti da
# WorkManager per nome, a partire da una stringa messa in coda mesi prima. Se R8
# li rinomina, il lavoro in coda non trova piu' la sua classe e muore in
# silenzio: esattamente il guasto che il guardiano esiste per evitare.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Tink, sotto EncryptedSharedPreferences -----------------------------------
# Le chiavi API stanno cifrate col Keystore, e sotto c'e' Tink, che registra i
# suoi gestori di chiave per nome e legge i propri protobuf per riflessione. Un
# `-keep` largo costa qualche decina di kilobyte; sbagliarlo costa le chiavi
# illeggibili su un APK che pero' compila, cioe' il guasto peggiore: si scopre
# dopo l'installazione.
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.proto.** { *; }
-dontwarn com.google.crypto.tink.**

# Tink cerca due classi solo su JVM da scrivania, che su Android non ci sono.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
