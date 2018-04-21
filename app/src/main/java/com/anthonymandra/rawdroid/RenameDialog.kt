package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import com.anthonymandra.content.Meta
import com.anthonymandra.framework.AsyncTask
import com.anthonymandra.framework.DocumentActivity
import com.anthonymandra.framework.DocumentUtil
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.util.ImageUtil
import kotlinx.android.synthetic.main.format_name.*
import java.io.IOException
import java.util.*

class RenameDialog(
        activity: Activity,
        private val itemsToRename: Collection<Uri>) : AlertDialog(activity) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.format_name)
        setTitle(context.getString(R.string.renameImages))
        setCanceledOnTouchOutside(true)

        nameTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: Editable) {
                updateExample()
            }
        })

        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("SetTextI18n")
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                updateExample()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        renameButton.setOnClickListener {
            val customName = nameTextView.text.toString()
            val selected = formatSpinner.selectedItemPosition
            RenameTask().execute(itemsToRename, selected, customName)
        }
        // TODO: Add buttons
    }

    // TODO: This should be meta object to make updates easier
    inner class RenameTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            if (params[0] !is List<*> || (params[0] as List<*>)[0] !is Uri)
                throw IllegalArgumentException()

            val totalImages = params[0] as List<Uri>
            val remainingImages = ArrayList(totalImages)

            val format = params[1] as Int
            val customName = params[2] as String

            var counter = 0
            val total = totalImages.size
            val operations = ArrayList<ContentProviderOperation>()

            try {
                for (image in totalImages) {
                    ++counter
                    val rename = formatRename(format, customName, counter, total)

                    rename ?: return false

                    renameImage(image, rename, operations)

                    if (renameImage(image, rename, operations)) {
                        remainingImages.remove(image)
                    }
                }
            } catch (e: DocumentActivity.WritePermissionException) {
                e.printStackTrace()
                return false
            } catch (e: IOException) {
                e.printStackTrace()
            }
            // TODO: FIXME
//            MetaUtil.updateMetaDatabase(this@CoreActivity, operations)
            return true
        }
    }

    @Throws(IOException::class)
    fun renameImage(source: Uri, baseName: String, updates: ArrayList<ContentProviderOperation>): Boolean {
        val srcFile = UsefulDocumentFile.fromUri(context, source)
        val xmpFile = ImageUtil.getXmpFile(context, source)
        val jpgFile = ImageUtil.getJpgFile(context, source)

        val filename = srcFile.name
        val sourceExt = filename.substring(filename.lastIndexOf("."), filename.length)

        val srcRename = baseName + sourceExt
        val xmpRename = "$baseName.xmp"
        val jpgRename = "$baseName.jpg"

        // Do src first in case it's a jpg
        if (srcFile.renameTo(srcRename)) {
            var rename = DocumentUtil.getNeighborUri(source, srcRename)
            if (rename == null) {
                val parent = srcFile.parentFile ?: return false
                val file = parent.findFile(srcRename) ?: return false
                rename = file.uri
            }
            val imageValues = ContentValues()
            imageValues.put(Meta.NAME, srcRename)
            imageValues.put(Meta.URI, rename!!.toString())

            updates.add(
                ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
                    .withSelection(Meta.URI_SELECTION, arrayOf(source.toString()))
                    .withValues(imageValues)
                    .build())
        } else
            return false

        xmpFile.renameTo(xmpRename)

        if (jpgFile.renameTo(jpgRename)) {
            var rename = DocumentUtil.getNeighborUri(source, srcRename)
            if (rename == null) {
                val parent = srcFile.parentFile ?: return false
                val file = parent.findFile(jpgRename) ?: return false
                rename = file.uri
            }

            val jpgValues = ContentValues()
            jpgValues.put(Meta.NAME, jpgRename)
            jpgValues.put(Meta.URI, rename!!.toString())

            updates.add(
                ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
                    .withSelection(Meta.URI_SELECTION, arrayOf(jpgFile.uri.toString()))
                    .withValues(jpgValues)
                    .build())
        }

        return true
    }

    @SuppressLint("SetTextI18n")
    private fun updateExample() {
        exampleTextView.text = "Ex: " + formatRename(formatSpinner.selectedItemPosition,
                nameTextView.text.toString(),
                itemsToRename.size - 1,
                itemsToRename.size)
    }

    private fun numDigits(x: Int): Int {
        return when {
            x < 10 -> 1
            x < 100 -> 2
            x < 1000 -> 3
            x < 10000 -> 4
            x < 100000 -> 5
            x < 1000000 -> 6
            x < 10000000 -> 7
            x < 100000000 -> 8
            x < 1000000000 -> 9
            else -> 10
        }
    }

    private fun formatRename(format: Int, baseName: String, index: Int, total: Int): String? {
        val sequencer = "%0" + numDigits(total) + "d"

        var rename: String? = null
        when (format) {
            0 -> rename = baseName + "-" + String.format(sequencer, index)
            1 -> rename = baseName + " (" + String.format(sequencer, index) + " of " + total + ")"
        }

        return rename
    }
}