package it.myacamperlife.app.ui.foto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import it.myacamperlife.app.dominio.Miniature
import java.io.File

/**
 * Carica le immagini dell'archivio. **Senza librerie.**
 *
 * Coil o Glide farebbero questo lavoro meglio e in tre righe, e costerebbero un
 * paio di megabyte di APK: la stessa cifra per cui e' stato tolto il
 * riconoscimento del testo. Qui servono due cose sole — una miniatura per
 * l'elenco e un'immagine grande per guardarla — e stanno in centoventi righe di
 * `BitmapFactory`.
 *
 * Le tre cose che vanno fatte bene, e che rendono questo file meno banale di
 * quanto sembri:
 *
 * - **si sottocampiona sempre.** Uno scatto da dodici megapixel occupa
 *   quarantotto megabyte in memoria come ARGB_8888; una lista con trenta foto
 *   dentro chiuderebbe l'app al terzo scorrimento. Si decodifica alla dimensione
 *   che serve, non a quella che il file ha
 * - **si rispetta l'orientamento EXIF.** La fotocamera di sistema quasi mai
 *   ruota i pixel: scrive un tag e lascia fare a chi legge. Ignorarlo mette tutte
 *   le foto verticali coricate su un fianco
 * - **si tiene una cache.** Riscorrere una lista non deve ridecodificare, e la
 *   cache e' misurata in byte e non in numero di elementi, perche' e' la memoria
 *   il limite che conta
 *
 * Non si decodifica **mai** sul thread principale: lo garantisce chi chiama,
 * dalla composizione, con un `produceState` su `Dispatchers.IO`.
 */
object Immagini {

    /**
     * Un ottavo della memoria concessa all'app.
     *
     * E' la proporzione che tutti usano, e il motivo e' che non e' la nostra
     * memoria: e' quella che il sistema ci presta, e prendersene troppa fa
     * chiudere l'app quando arriva una foto nuova.
     */
    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * L'immagine di [file], ridotta perche' il suo lato lungo stia entro
     * [latoMassimo]. `null` se il file non c'e' piu' o non e' un'immagine.
     *
     * La chiave della cache porta la dimensione richiesta: la miniatura e
     * l'immagine grande dello stesso file sono due voci distinte, e chiedere la
     * seconda non butta via la prima.
     */
    fun carica(file: File, latoMassimo: Int): Bitmap? {
        if (!file.isFile) return null

        val chiave = "${file.absolutePath}|${file.lastModified()}|$latoMassimo"
        cache.get(chiave)?.let { return it }

        val misura = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, misura) }
        if (misura.outWidth <= 0 || misura.outHeight <= 0) return null

        val opzioni = BitmapFactory.Options().apply {
            inSampleSize = Miniature.quantoRidurre(misura.outWidth, misura.outHeight, latoMassimo)
            // RGB_565 dimezza la memoria e su una fotografia non si vede la
            // differenza: non c'e' trasparenza da conservare e non ci si fa
            // sopra fotoritocco.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val letta = runCatching { BitmapFactory.decodeFile(file.absolutePath, opzioni) }
            .getOrNull() ?: return null

        val girata = raddrizza(letta, file)
        cache.put(chiave, girata)
        return girata
    }

    /**
     * Ruota secondo il tag EXIF.
     *
     * Si usa `android.media.ExifInterface` e non quella di AndroidX: legge meno
     * formati esotici, ma i file che questa app apre li ha scritti la fotocamera
     * di sistema in JPEG, e non vale una dipendenza in piu'.
     *
     * Se il tag manca o e' quello normale si restituisce la bitmap **cosi' com'e'**,
     * senza copiarla: e' il caso piu' frequente, e una copia inutile e' un'altra
     * immagine in memoria.
     */
    private fun raddrizza(bitmap: Bitmap, file: File): Bitmap {
        val orientamento = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrice = Matrix()
        when (orientamento) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrice.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrice.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrice.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrice.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrice.postScale(1f, -1f)
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrice, true)
        }.getOrDefault(bitmap)
    }

    /** Il lato di una miniatura da elenco, in pixel. */
    const val MINIATURA = 256

    /**
     * Il lato dell'immagine a schermo pieno.
     *
     * Milleduecento e non la risoluzione dello schermo: su un telefono moderno
     * sarebbero tre volte i pixel per una differenza che non si vede tenendo il
     * telefono in mano, e tre volte la memoria.
     */
    const val GRANDE = 1200
}
