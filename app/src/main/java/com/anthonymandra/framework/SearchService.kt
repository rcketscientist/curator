package com.anthonymandra.framework

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.LocalBroadcastManager
import com.anthonymandra.rawdroid.data.AppDatabase
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.FolderEntity
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.util.ImageUtil
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.concurrent.atomic.AtomicInteger

class SearchService : IntentService("SearchService") {

    val dataRepo = DataRepository.getInstance(AppDatabase.getInstance(this))
    var parentMap = mutableMapOf<String, Long>()

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (ACTION_SEARCH == action) {
                val uris = intent.getStringArrayExtra(EXTRA_DOCUMENT_TREE_URI_ROOTS)
                val skip = intent.getStringArrayExtra(EXTRA_SKIP)
                handleActionSearch(uris, skip)
            }
        }
    }

    private fun handleActionSearch(uriRoots: Array<String>?, alwaysExcludeDir: Array<String>?) {
        dataRepo.streamParents.subscribe {
            it.associateTo(parentMap) { Pair(it.documentUri,it.id) }
        }

        mImageCount.set(0)
        var broadcast = Intent(BROADCAST_SEARCH_STARTED)
        LocalBroadcastManager.getInstance(this@SearchService).sendBroadcast(broadcast)

        val pool = ForkJoinPool()     // This should be a common pool for the app.  Currently only use
        val foundImages = HashSet<MetadataEntity>()
        if (uriRoots != null) {
            for (uri in uriRoots) {
                val task = SearchTask(this, UsefulDocumentFile.fromUri(this, Uri.parse(uri)), alwaysExcludeDir)
                val searchResults = pool.invoke(task)
                if (searchResults != null)
                    foundImages.addAll(searchResults)
            }
        }

        val uriStrings = HashSet<String>()
        if (foundImages.size > 0) {
            dataRepo.insertImages(*foundImages.toTypedArray())
        }

        broadcast = Intent(BROADCAST_SEARCH_COMPLETE)
            .putExtra(EXTRA_IMAGE_IDS, foundImages.map { it.id }.toLongArray())
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    private inner class SearchTask
        internal constructor(internal val mContext: Context,
                             internal val mRoot: UsefulDocumentFile?,
                             internal val mExcludeDir: Array<String>?) : RecursiveTask<Set<MetadataEntity>>() {
        internal var foundImages: MutableSet<MetadataEntity> = HashSet()

        override fun compute(): Set<MetadataEntity>? {
            if (mRoot == null)
                return null
            val name = mRoot.name
            if (name == null || name.startsWith("."))
                return null

            val contents = mRoot.listFiles() ?: return null

            if (mExcludeDir != null) {
                for (skip in mExcludeDir) {
                    if (mRoot.uri.toString().startsWith(skip))
                        return null
                }
            }

            val imageFiles = listImages(contents) ?: return null // .nomedia, don't recurse

            // Recursion pass
            val forks = LinkedList<SearchTask>()
            for (f in contents) {
                if (f == null)
                    continue

                if (f.cachedData != null && f.cachedData!!.isDirectory && f.cachedData!!.canRead) {
                    val child = SearchTask(mContext, f, mExcludeDir)
                    forks.add(child)
                    child.fork()
                }
            }

            if (imageFiles.isNotEmpty()) {
                val imageCount = mImageCount.addAndGet(imageFiles.size)
                val broadcast = Intent(BROADCAST_FOUND_IMAGES)
                    .putExtra(EXTRA_NUM_IMAGES, imageCount)
                LocalBroadcastManager.getInstance(this@SearchService).sendBroadcast(broadcast)

                for (image in imageFiles) {
                    val uri = image.uri ?: // Somehow we can get null uris in here...
                    continue    // https://bitbucket.org/rcketscientist/rawdroid/issues/230/coreactivityjava-line-677-crashlytics

                    foundImages.add(getImageFileInfo(image))
                }
            }

            for (task in forks) {
                val childImages = task.join()
                if (childImages != null)
                    foundImages.addAll(childImages)
            }

            return foundImages
        }
    }

    // TODO: This should be a custom DocumentProvider
    fun getImageFileInfo(file: UsefulDocumentFile): MetadataEntity {
        val fd = file.cachedData
        val metadata = MetadataEntity()

        var parent: String? = null
        if (fd != null) {
            metadata.name = fd.name
            parent = fd.parent.toString()
            metadata.timestamp = fd.lastModified.toString()
        } else {
            val docParent = file.parentFile
            if (docParent != null) {
                parent = docParent.uri.toString()
            }
        }

        if (parent != null) {
            if (parentMap.containsKey(parent)) {
                metadata.parentId = parentMap[parent]!!
            } else {
                metadata.parentId = dataRepo.insertParent(FolderEntity(parent))
            }
        }

        metadata.documentId = file.documentId
        metadata.uri = file.uri.toString()
        metadata.type = ImageUtil.getImageType(this, file.uri).value
        return metadata
    }

    companion object {
        private val TAG = SearchService::class.java.simpleName
        /**
         * Broadcast ID when parsing is complete
         */
        const val BROADCAST_SEARCH_STARTED = "com.anthonymandra.framework.action.BROADCAST_SEARCH_STARTED"
        /**
         * Broadcast ID when parsing is complete
         */
        const val BROADCAST_SEARCH_COMPLETE = "com.anthonymandra.framework.action.BROADCAST_SEARCH_COMPLETE"
        /**
         * Broadcast extra containing uris for the discovered images
         */
        const val EXTRA_IMAGE_IDS = "com.anthonymandra.framework.action.EXTRA_IMAGE_IDS"

        /**
         * Broadcast ID when images are found, sent after every folder with hits
         */
        const val BROADCAST_FOUND_IMAGES = "com.anthonymandra.framework.action.BROADCAST_FOUND_IMAGES"
        /**
         * Broadcast extra containing running total of images found in the current search
         */
        const val EXTRA_NUM_IMAGES = "com.anthonymandra.framework.action.EXTRA_NUM_IMAGES"
        private val mImageCount = AtomicInteger(0)

        private const val ACTION_SEARCH = "com.anthonymandra.framework.action.ACTION_SEARCH"
        private const val EXTRA_FILEPATH_ROOTS = "com.anthonymandra.framework.extra.EXTRA_FILEPATH_ROOTS"
        private const val EXTRA_DOCUMENT_TREE_URI_ROOTS = "com.anthonymandra.framework.extra.EXTRA_DOCUMENT_TREE_URI_ROOTS"
        private const val EXTRA_SKIP = "com.anthonymandra.framework.extra.EXTRA_SKIP"

        fun startActionSearch(context: Context, filePathRoots: Array<String>?, documentTreeUris: Array<String>, skip: Array<String>) {
            val intent = Intent(context, SearchService::class.java)
            intent.action = ACTION_SEARCH
            intent.putExtra(EXTRA_FILEPATH_ROOTS, filePathRoots)
            intent.putExtra(EXTRA_DOCUMENT_TREE_URI_ROOTS, documentTreeUris)
            intent.putExtra(EXTRA_SKIP, skip)
            context.startService(intent)
        }

        /**
         * emulate File.listFiles(ImageFilter)
         * @param files files to process
         * @return list of image files, or null if .nomedia is encountered
         */
        fun listImages(files: Array<UsefulDocumentFile>): Array<UsefulDocumentFile>? {
            val result = ArrayList<UsefulDocumentFile>(files.size)
            for (file in files) {
                if (file.cachedData == null)
                    continue
                val name = file.cachedData!!.name ?: continue
                if (".nomedia" == name)
                // if .nomedia clear results and return
                    return null
                if (ImageUtil.isImage(name))
                    result.add(file)
            }
            return result.toTypedArray()
        }
    }
}
