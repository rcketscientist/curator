@file:Suppress("unused")

package com.anthonymandra.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.adobe.xmp.XMPException
import com.adobe.xmp.XMPMeta
import com.adobe.xmp.XMPMetaFactory
import com.adobe.xmp.impl.XMPMetaImpl
import com.adobe.xmp.options.PropertyOptions
import com.adobe.xmp.options.SerializeOptions
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.ImageInfo
import com.crashlytics.android.Crashlytics
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.MetadataException
import com.drew.metadata.Schema
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.exif.PanasonicRawIFD0Directory
import com.drew.metadata.exif.makernotes.*
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.drew.metadata.xmp.XmpReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object MetaUtil {
    private val TAG = MetaUtil::class.java.simpleName

    internal var mLibrawFormatter = SimpleDateFormat("EEE MMM d hh:mm:ss yyyy")
    private val mMetaExtractorFormat = SimpleDateFormat("yyyy:MM:dd H:m:s")

    val LABEL = XmpProperty(Schema.XMP_PROPERTIES, "xmp:label")
    val RATING = XmpProperty(Schema.XMP_PROPERTIES, "xmp:rating")
    val SUBJECT = XmpProperty(Schema.DUBLIN_CORE_SPECIFIC_PROPERTIES, "dc:subject")
    val CREATOR = XmpProperty(Schema.DUBLIN_CORE_SPECIFIC_PROPERTIES, "dc:Creator")    //TODO: TEST

    class XmpProperty internal constructor(internal val Schema: String, val Name: String)

    fun readMetadata(c: Context, uri: Uri): Metadata {
        val meta = readMeta(c, uri)
        return readXmp(c, uri, meta)
    }

    /**
     * Returns metadata from the given image uri
     * @param c
     * @param uri
     * @return
     */
    private fun readMeta(c: Context, uri: Uri): Metadata {
        var image: InputStream? = null
        var meta = Metadata()
        try {
            image = c.contentResolver.openInputStream(uri)
            meta = ImageMetadataReader.readMetadata(image)
        } catch (e: Exception) {
            Crashlytics.setString("readMetaUri", uri.toString())
            Crashlytics.logException(e)
        } finally {
            Util.closeSilently(image)
        }
        return meta
    }

    /**
     * Reads associated xmp file if it exists and adds the data to meta
     * @param uri image file
     */
    private fun readXmp(c: Context, uri: Uri, meta: Metadata = Metadata()): Metadata {
        val xmpDoc = ImageUtil.getXmpFile(c, uri)
        if (xmpDoc == null || !xmpDoc.exists())
            return meta

        var xmpStream: InputStream? = null
        return try {
            xmpStream = FileUtil.getInputStream(c, xmpDoc.uri)
            val reader = XmpReader()
            val buffer = ByteArray(xmpStream!!.available())
            xmpStream.read(buffer)
            reader.extract(buffer, meta)
            meta
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open XMP.", e)
            meta
        } finally {
            Util.closeSilently(xmpStream)
        }
    }

    fun readXmp(c: Context, file: UsefulDocumentFile): XMPMeta {
        try {
            FileUtil.getInputStream(c, file.uri).use { `is` -> return readXmp(`is`) }
        } catch (e: IOException) {
            e.printStackTrace()
            return XMPMetaImpl()
        } catch (e: XMPException) // TODO: What might this mean?
        {
            e.printStackTrace()
            return XMPMetaImpl()
        } catch (e: IllegalArgumentException) //This nonsense is thrown when the file doesn't exist in SAF...
        {
            e.printStackTrace()
            return XMPMetaImpl()
        }

    }

    @Throws(XMPException::class)
    private fun readXmp(stream: InputStream): XMPMeta {
        return XMPMetaFactory.parse(stream)
    }

    /**
     * Serializes the `XMPMeta` into an `OutputStream`
     * @param c Context
     * @param file xmp file
     * @param meta populated metadata
     * @return serialize success
     */
    @Throws(IOException::class, XMPException::class)
    fun writeXmp(c: Context, file: UsefulDocumentFile, meta: XMPMeta) {
        c.contentResolver.openOutputStream(file.uri)!!.use { os -> writeXmp(os, meta) }
    }

    /**
     * Serializes the XmpDirectory component of `Metadata` into an `OutputStream`
     * @param os Destination for the xmp data
     * @param meta populated metadata
     */
    @Throws(XMPException::class)
    fun writeXmp(os: OutputStream, meta: XMPMeta) {
        val so = SerializeOptions().setOmitPacketWrapper(true)
        XMPMetaFactory.serialize(meta, os, so)
    }

    /**
     * Read meta data and convert to database model
     * @param c context
     * @param entity image to parse
     * @param entity image to parse
     * @return processed metadata values or null if failed
     */
    fun readMetadata(c: Context, repo: DataRepository, entity: ImageInfo) : ImageInfo{
        val meta = readMetadata(c, entity.uri.toUri())
        return populateMetadata(repo, entity, meta)
    }

    fun readMetadata(repo: DataRepository, meta: Metadata): ImageInfo {
        val entity = ImageInfo()
        return populateMetadata(repo, entity, meta)
    }

    private fun populateMetadata(repo: DataRepository, entity: ImageInfo, meta: Metadata): ImageInfo {
        entity.altitude = getAltitude(meta)
        entity.aperture = getAperture(meta)
        entity.exposure = getExposure(meta)
        entity.flash = getFlash(meta)
        entity.focalLength = getFocalLength(meta)
        entity.width = getImageWidth(meta)
        entity.height = getImageHeight(meta)
        entity.iso = getIso(meta)
        entity.latitude = getLatitude(meta)
        entity.longitude = getLongitude(meta)
        entity.model = getModel(meta)

        entity.orientation = getOrientation(meta)
        val rawDate = getDateTime(meta)
        if (rawDate != null)
        // Don't overwrite null since we can rely on file time
        {
            try {
                val date = mMetaExtractorFormat.parse(rawDate)
                entity.timestamp = date.time
            } catch (e: ParseException) {
                Crashlytics.logException(e)
            } catch (e: ArrayIndexOutOfBoundsException) {
                Crashlytics.logException(e)
            }

        }
        entity.whiteBalance = getWhiteBalance(meta)

        entity.rating = getRating(meta)?.toFloat()
        getSubjectList(meta)?.let { entity.subjectIds = repo.convertToSubjectIds(it) }
        entity.label = getLabel(meta)
        entity.lens = getLensModel(meta)
        entity.driveMode = getDriveMode(meta)
        entity.exposureMode = getExposureMode(meta)
        entity.exposureProgram = getExposureProgram(meta)
        entity.processed = true

        return entity
    }

    @WorkerThread
    fun parseMetadata(context: Context, images: List<ImageInfo>) {
        val repo = DataRepository.getInstance(context)

        images.forEach {
            val metadata = MetaUtil.readMetadata(context, repo, it)
            if (metadata.processed) {
                repo.updateMeta(it)
            }
        }
    }

    /**
     * --- Meta Getters ----------------------------------------------------------------------------
     */

    private fun getDescription(meta: Metadata, tag: Int): String? {
        for (dir in meta.directories) {
            if (dir.containsTag(tag))
                return dir.getDescription(tag)
        }
        return null
    }

    private fun <T : Directory> getStringArray(meta: Metadata, type: Class<T>, tag: Int): Array<String>? {
        val dir = meta.getFirstDirectoryOfType(type)
        return if (dir == null || !dir.containsTag(tag)) null else dir.getStringArray(tag)
    }

    private fun <T : Directory> getInt (meta: Metadata, type: Class<T>, tag: Int): Int {
        val dir = meta.getFirstDirectoryOfType(type)
        var result = 0
        if (dir == null || !dir.containsTag(tag))
            return result
        try {
            result = dir.getInt(tag)
        } catch (e: MetadataException) {
            e.printStackTrace()
        }

        return result
    }

    private fun <T : Directory> getDouble(meta: Metadata, type: Class<T>, tag: Int): Double? {
        val dir = meta.getFirstDirectoryOfType(type)
        if (dir == null || !dir.containsTag(tag))
            return null
        try {
            return dir.getDouble(tag)
        } catch (e: MetadataException) {
            e.printStackTrace()
        }

        return null
    }

    private fun getExifIFD0Description(meta: Metadata, tag: Int): String? {
        val exif = meta.getFirstDirectoryOfType(ExifIFD0Directory::class.java) ?: return null
        return exif.getDescription(tag)
    }

    private fun getAperture(meta: Metadata): String? {
        var aperture = getDescription(meta, ExifSubIFDDirectory.TAG_FNUMBER)
        if (aperture == null)
            aperture = getDescription(meta, ExifSubIFDDirectory.TAG_APERTURE)
        return aperture
    }

    private fun getExposure(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
    }

    private fun getImageHeight(meta: Metadata): Int {
        var height = getInt(meta, ExifSubIFDDirectory::class.java, ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
        if (height == 0)
            height = getInt(meta, ExifIFD0Directory::class.java, ExifIFD0Directory.TAG_IMAGE_HEIGHT)
        if (height == 0)
            height = getInt(meta, JpegDirectory::class.java, JpegDirectory.TAG_IMAGE_HEIGHT)
        if (height == 0)
            height = getInt(meta, PanasonicRawIFD0Directory::class.java, PanasonicRawIFD0Directory.TagSensorHeight)
        return height
    }

    private fun getImageWidth(meta: Metadata): Int {
        var width = getInt(meta, ExifSubIFDDirectory::class.java, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
        if (width == 0)
            width = getInt(meta, ExifIFD0Directory::class.java, ExifIFD0Directory.TAG_IMAGE_WIDTH)
        if (width == 0)
            width = getInt(meta, JpegDirectory::class.java, JpegDirectory.TAG_IMAGE_WIDTH)
        if (width == 0)
            width = getInt(meta, PanasonicRawIFD0Directory::class.java, PanasonicRawIFD0Directory.TagSensorWidth)
        return width
    }

    private fun getFocalLength(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_FOCAL_LENGTH)
    }

    private fun getFlash(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_FLASH)
    }

    private fun getShutterSpeed(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_SHUTTER_SPEED)
    }

    private fun getWhiteBalance(meta: Metadata): String? {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory::class.java))
	        return meta.getFirstDirectoryOfType(CanonMakernoteDirectory::class.java).getDescription(CanonMakernoteDirectory.FocalLength.TAG_WHITE_BALANCE)
	    if (meta.containsDirectoryOfType(PanasonicMakernoteDirectory::class.java))
	        return meta.getFirstDirectoryOfType(PanasonicMakernoteDirectory::class.java).getDescription(PanasonicMakernoteDirectory.TAG_WHITE_BALANCE)
	    if (meta.containsDirectoryOfType(FujifilmMakernoteDirectory::class.java))
	        return meta.getFirstDirectoryOfType(FujifilmMakernoteDirectory::class.java).getDescription(FujifilmMakernoteDirectory.TAG_WHITE_BALANCE)
	    if (meta.containsDirectoryOfType(LeicaMakernoteDirectory::class.java))
	        return meta.getFirstDirectoryOfType(LeicaMakernoteDirectory::class.java).getDescription(LeicaMakernoteDirectory.TAG_WHITE_BALANCE)
        return getDescription(meta, ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE)
    }

    private fun getExposureProgram(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM)
    }

    private fun getExposureMode(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_EXPOSURE_MODE)
    }

    private fun getLensMake(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_LENS_MAKE)
    }

    private fun getLensModel(meta: Metadata): String? {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(CanonMakernoteDirectory::class.java)!!.getDescription(CanonMakernoteDirectory.TAG_LENS_MODEL)
        if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory::class.java)!!.getDescription(NikonType2MakernoteDirectory.TAG_LENS)
        if (meta.containsDirectoryOfType(SigmaMakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(SigmaMakernoteDirectory::class.java)!!.getDescription(SigmaMakernoteDirectory.TAG_LENS_TYPE)
        if (meta.containsDirectoryOfType(OlympusEquipmentMakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(OlympusEquipmentMakernoteDirectory::class.java)!!.getDescription(OlympusEquipmentMakernoteDirectory.TAG_LENS_TYPE)
        // We prefer the exif over some maker notes
        val lens = getDescription(meta, ExifSubIFDDirectory.TAG_LENS_MODEL)
        if (lens == null) {
            // but use maker if exif doesn't exist
            if (meta.containsDirectoryOfType(SonyType1MakernoteDirectory::class.java))
                return meta.getFirstDirectoryOfType(SonyType1MakernoteDirectory::class.java)!!.getDescription(SonyType1MakernoteDirectory.TAG_LENS_ID)
        }
        return lens
    }

    private fun getDriveMode(meta: Metadata): String? {
        if (meta.containsDirectoryOfType(CanonMakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(CanonMakernoteDirectory::class.java)!!.getDescription(CanonMakernoteDirectory.CameraSettings.TAG_CONTINUOUS_DRIVE_MODE)
        if (meta.containsDirectoryOfType(NikonType2MakernoteDirectory::class.java))
            return meta.getFirstDirectoryOfType(NikonType2MakernoteDirectory::class.java)!!.getDescription(NikonType2MakernoteDirectory.TAG_SHOOTING_MODE)
        return if (meta.containsDirectoryOfType(OlympusCameraSettingsMakernoteDirectory::class.java)) meta.getFirstDirectoryOfType(OlympusCameraSettingsMakernoteDirectory::class.java)!!.getDescription(OlympusCameraSettingsMakernoteDirectory.TagDriveMode) else null
    }

    private fun getIso(meta: Metadata): String? {
        var iso = getDescription(meta, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
        if (iso == null)
            iso = getDescription(meta, PanasonicRawIFD0Directory.TagIso)
        return iso
    }

    private fun getFNumber(meta: Metadata): String? {
        val exif = meta.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        return if (exif != null) {
            if (exif.containsTag(ExifSubIFDDirectory.TAG_FNUMBER))
                exif.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)
            else
                exif.getDescription(ExifSubIFDDirectory.TAG_APERTURE)
        } else null
    }

    private fun getDateTime(meta: Metadata): String? {
        return getDescription(meta, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
    }

    private fun getMake(meta: Metadata): String? {
        var make = getExifIFD0Description(meta, ExifIFD0Directory.TAG_MAKE)
        if (make == null)
            make = getDescription(meta, PanasonicRawIFD0Directory.TagMake)
        return make
    }

    private fun getModel(meta: Metadata): String? {
        var model = getExifIFD0Description(meta, ExifIFD0Directory.TAG_MODEL)
        if (model == null)
            model = getDescription(meta, PanasonicRawIFD0Directory.TagModel)
        return model
    }

    private fun getOrientation(meta: Metadata): Int {
        return getInt(meta, ExifIFD0Directory::class.java, ExifIFD0Directory.TAG_ORIENTATION)
    }

    fun getRotation(orientation: Int): Int {
        return when (orientation) {
            1 -> 0
            3 -> 180
            6 -> 90
            8 -> 270
            90 -> 90
            180 -> 180
            270 -> 270
            else -> 0
        }
    }

    private fun getAltitude(meta: Metadata): String? {
        return getDescription(meta, GpsDirectory.TAG_ALTITUDE)
    }

    private fun getLatitude(meta: Metadata): String? {
        return getDescription(meta, GpsDirectory.TAG_LATITUDE)
    }

    private fun getLongitude(meta: Metadata): String? {
        return getDescription(meta, GpsDirectory.TAG_LONGITUDE)
    }

    private fun getRating(meta: Metadata): Double? {
        for (dir in meta.getDirectoriesOfType(XmpDirectory::class.java)) {
            val xmp = (dir as XmpDirectory).xmpMeta
            try {
                return xmp.getPropertyDouble(RATING.Schema, RATING.Name)
            } catch (e: XMPException) {
                // do nothing
            }

        }
        return null
    }

    private fun getLabel(meta: Metadata): String? {
        for (dir in meta.getDirectoriesOfType(XmpDirectory::class.java)) {
            val xmp = (dir as XmpDirectory).xmpMeta
            try {
                return xmp.getPropertyString(LABEL.Schema, LABEL.Name)
            } catch (e: XMPException) {
                // do nothing
            }

        }
        return null
    }

    private fun getSubjectList(meta: Metadata): List<String>? {
        for (dir in meta.getDirectoriesOfType(XmpDirectory::class.java)) {
            val xmp = (dir as XmpDirectory).xmpMeta
            try {
                //XMP iterators are 1-based
                val count = xmp.countArrayItems(SUBJECT.Schema, SUBJECT.Name)
                val subjects = ArrayList<String>(count)
                for (i in 1..count) {
                    subjects.add(xmp.getArrayItem(SUBJECT.Schema, SUBJECT.Name, i).value)
                }
                return subjects
            } catch (e: XMPException) {
                // do nothing
            }

        }
        return null
    }

    private fun getSubject(meta: Metadata): Array<String?>? {
        for (dir in meta.getDirectoriesOfType(XmpDirectory::class.java)) {
            val xmp = (dir as XmpDirectory).xmpMeta
            try {
                //XMP iterators are 1-based
                val count = xmp.countArrayItems(SUBJECT.Schema, SUBJECT.Name)
                val subjects = arrayOfNulls<String>(count)
                for (i in 1..count) {
                    subjects[i - 1] = xmp.getArrayItem(SUBJECT.Schema, SUBJECT.Name, i).value
                }
                return subjects
            } catch (e: XMPException) {
                // do nothing
            }

        }
        return null
    }

    private fun checkXmpDirectory(meta: Metadata) {
        if (!meta.containsDirectoryOfType(XmpDirectory::class.java))
            meta.addDirectory(XmpDirectory())
    }

    /**
     * Update the xmp:rating, passing null will delete existing value.
     */
    fun updateRating(meta: Metadata, rating: Int?) {
        updateXmpDouble(meta, RATING, rating?.toDouble())
    }

    /**
     * Update the xmp:rating, passing null will delete existing value.
     */
    fun updateRating(meta: Metadata, rating: Double?) {
        updateXmpDouble(meta, RATING, rating)
    }

    /**
     * Update the xmp:label, passing null will delete existing value.
     */
    fun updateLabel(meta: Metadata, label: String?) {
        updateXmpString(meta, LABEL, label)
    }

    /**
     * Update the xmp:subject, passing null will delete existing value.
     */
    fun updateSubject(meta: Metadata, subject: Array<String>) {
        updateXmpStringArray(meta, SUBJECT, subject)
    }

    private fun updateXmpString(meta: Metadata, prop: XmpProperty, value: String?) {
        checkXmpDirectory(meta)

        val xmp = meta.getFirstDirectoryOfType(XmpDirectory::class.java) ?: return

        updateXmpString(xmp.xmpMeta, prop, value)
    }

    fun updateXmpString(meta: XMPMeta, prop: XmpProperty, value: String?) {
        if (value == null) {
            meta.deleteProperty(prop.Schema, prop.Name)
        } else {
            try {
                meta.setProperty(prop.Schema, prop.Name, value)
            } catch (e: XMPException) {
                e.printStackTrace()
            }

        }
    }

    private fun updateXmpStringArray(meta: Metadata, prop: XmpProperty, value: Array<String>) {
        checkXmpDirectory(meta)

        val xmp = meta.getFirstDirectoryOfType(XmpDirectory::class.java) ?: return

        updateXmpStringArray(xmp.xmpMeta, prop, value)
    }

    fun updateXmpStringArray(meta: XMPMeta, prop: XmpProperty, value: Array<String>?) {
        meta.deleteProperty(prop.Schema, prop.Name)
        if (value == null || value.isEmpty())
            return  // If the value is invalid this is just a delete.

        val po = PropertyOptions().setArray(true)
        for (item in value) {
            try {
                meta.appendArrayItem(prop.Schema, prop.Name, po, item, null)
            } catch (e: XMPException) {
                e.printStackTrace()    // TODO: We should throw this
            }

        }
    }

    private fun updateXmpDouble(meta: Metadata, prop: XmpProperty, value: Double?) {
        checkXmpDirectory(meta)

        val xmp = meta.getFirstDirectoryOfType(XmpDirectory::class.java) ?: return

        updateXmpDouble(xmp.xmpMeta, prop, value)
    }

    private fun updateXmpDouble(meta: XMPMeta, prop: XmpProperty, value: Double?) {
        if (value == null) {
            meta.deleteProperty(prop.Schema, prop.Name)
        } else {
            try {
                meta.setPropertyDouble(prop.Schema, prop.Name, value)
            } catch (e: XMPException) {
                e.printStackTrace()    // TODO: We should throw this
            }

        }
    }

    fun updateXmpInteger(meta: Metadata, prop: XmpProperty, value: Int?) {
        checkXmpDirectory(meta)

        val xmp = meta.getFirstDirectoryOfType(XmpDirectory::class.java) ?: return

        updateXmpInteger(xmp.xmpMeta, prop, value)
    }

    fun updateXmpInteger(meta: XMPMeta, prop: XmpProperty, value: Int?) {
        if (value == null) {
            meta.deleteProperty(prop.Schema, prop.Name)
        } else {
            try {
                meta.setPropertyInteger(prop.Schema, prop.Name, value)
            } catch (e: XMPException) {
                e.printStackTrace()    // TODO: We should throw this
            }

        }
    }
}
