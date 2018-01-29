package com.anthonymandra.rawdroid

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.util.TypedValue
import android.view.View
import com.anthonymandra.framework.FileUtil
import com.github.amlcurran.showcaseview.MorphShowcaseDrawer
import com.github.amlcurran.showcaseview.ShowcaseView
import com.github.amlcurran.showcaseview.targets.MorphViewTarget
import com.github.amlcurran.showcaseview.targets.PointTarget
import com.github.amlcurran.showcaseview.targets.Target
import kotlinx.android.synthetic.main.gallery.*
import java.io.File
import java.io.FileOutputStream

class TutorialActivity : GalleryActivity() {
    internal var tutorialStage = 0
//    private var tutorial: ShowcaseView? = null
    private val cacheDir = "tutorial"
    private val imageName = "tutorial"

    /**
     * Actual pixel width of the 48dp action item touch box.
     */
    private var actionItemWidth: Float = 0.toFloat()

    /**
     * Margin applied only to gallery items in addition to [.showcaseItemMargin].
     */
    private var galleryItemMargin: Float = 0.toFloat()

    /**
     * Margin applied to all showcase items.
     */
    private var showcaseItemMargin: Float = 0.toFloat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionItemWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics)
        showcaseItemMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
        galleryItemMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val tutorialDirectory = FileUtil.getDiskCacheDir(this, cacheDir)
        if (!tutorialDirectory.exists()) {
            if (!tutorialDirectory.mkdir())
                finish()
        }

        val assets = listOf(R.drawable.tutorial1, R.drawable.tutorial2, R.drawable.tutorial3, R.drawable.tutorial4, R.drawable.tutorial5)
        assets.forEachIndexed{ i, asset ->
            val output = File( tutorialDirectory, "$imageName$i.jpg")
            FileOutputStream(output).use {
                BitmapFactory.decodeResource(resources, asset).compress(Bitmap.CompressFormat.JPEG, 100, it)
                addDatabaseReference(Uri.fromFile(output))
            }
        }

        // FIXME: This needs a local datastore backing
//        updateMetaLoader(null,
//                Meta.URI + " LIKE ?",
//                arrayOf("%" + tutorialDirectory.name + "%"), null)

        val tutorial = ShowcaseView.Builder(this)//, true)
                .setShowcaseDrawer(MorphShowcaseDrawer(resources, showcaseItemMargin))
                .setContentTitle(R.string.tutorialWelcomeTitle)
                .setContentText(R.string.tutorialWelcomeText)
                .doNotBlockTouches()
                .setStyle(R.style.CustomShowcaseTheme2)
                .replaceEndButton(R.layout.tutorial_button)
                .build()
        tutorial.setOnClickListener(TutorialClickListener(tutorial))

        tutorial.setButtonText(getString(R.string.next))
        tutorial.setDetailTextAlignment(Layout.Alignment.ALIGN_OPPOSITE)
        tutorial.setShowcase(Target.NONE, false)
    }

    override fun onDestroy() {
        super.onDestroy()

        endContextMode()

        val tutorialDirectory = FileUtil.getDiskCacheDir(this, cacheDir)
        for (i in 1..5) {
            val oldImage = File( tutorialDirectory, "$imageName$i.jpg")
            removeDatabaseReference(Uri.fromFile(oldImage))
            oldImage.delete()
        }

        tutorialDirectory.delete()
    }

    private fun programmaticClick(position: Int) {
        if (galleryAdapter.itemCount < position + 1)
            closeTutorialWithError()
        else
            onItemClick(galleryAdapter, gridview.getChildAt(position), position, position.toLong())
    }

    private fun programmaticLongClick(position: Int) {
        if (galleryAdapter.itemCount < position + 1)
            closeTutorialWithError()
        else
            onItemLongClick(galleryAdapter, gridview.getChildAt(position), position, position.toLong())
    }

    private fun closeTutorialWithError() {
        setResult(RESULT_ERROR)
        finish()
    }

    private inner class TutorialClickListener(showcase: ShowcaseView) : View.OnClickListener {
        //Note: Don't animate coming from "NoShowcase" it flies in from off screen which is silly.

        val tutorial = showcase
        override fun onClick(v: View) {
            when (tutorialStage) {
                0 // Connection
                -> {
                    tutorial.setContentTitle(getString(R.string.tutorialConnectTitle))
                    tutorial.setContentText(getString(R.string.tutorialConnectText1))
                }
                1 // Connection
                -> tutorial.setContentText(getString(R.string.tutorialConnectText2))
                2 // Connection
                -> tutorial.setContentText(getString(R.string.tutorialConnectText3))
                3 // Connection
                -> tutorial.setContentText(getString(R.string.tutorialConnectText4))
                4 // Connection
                -> tutorial.setContentText(getString(R.string.tutorialConnectText5))
                5 // Search
                -> {
                    tutorial.setContentText(getString(R.string.tutorialFindImagesText))
                    setTutorialActionView(tutorial, R.id.galleryRefresh, false)
                }
                6 // Long Select
                -> {
                    tutorial.setContentTitle(getString(R.string.tutorialSelectTitle))
                    tutorial.setContentText(getString(R.string.tutorialSingleSelectText))
                    val view = gridview.getChildAt(0)
                    if (view != null)
                        tutorial.setShowcase(MorphViewTarget(view, galleryItemMargin), true)
                    else
                        tutorial.setShowcase(Target.NONE, false)    //TODO: User set an empty folder, somehow???
                }
                7 // Add select
                -> {
                    // If the user is lazy select for them
                    if (!isContextModeActive)
                        programmaticLongClick(0)

                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText))
                    val view = gridview.getChildAt(2)
                    if (view != null)
                        tutorial.setShowcase(MorphViewTarget(view, galleryItemMargin), true)
                    else
                        tutorial.setShowcase(Target.NONE, false)    //TODO: User set an empty folder, somehow???
                }
                8 // Select feedback
                -> {
                    // If the user is lazy select for them
                    if (galleryAdapter.selectedItemCount < 2) {
                        programmaticLongClick(0)
                        programmaticClick(2)
                    }

                    tutorial.setContentText(getString(R.string.tutorialMultiSelectText2))
                    setTutorialTitleView(tutorial, true)
                }
                9 // Select All
                -> {
                    endContextMode()

                    tutorial.setContentText(getString(R.string.tutorialSelectAll))
                    setTutorialActionView(tutorial, R.id.menu_selectAll, true)
                }
                10 // Exit Selection
                -> {
                    // If the user is lazy select for them
                    if (galleryAdapter.selectedItemCount < 1)
                        selectAll()

                    tutorial.setContentText(getString(R.string.tutorialExitSelectionText))
                    setTutorialHomeView(tutorial, true)
                }
                11 // Select between beginning
                -> {
                    endContextMode()

                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText1))
                    val view = gridview.getChildAt(1)        //WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(MorphViewTarget(view, galleryItemMargin), true)
                    else
                        tutorial.setShowcase(Target.NONE, false)    //TODO: User set an empty folder, somehow???
                }
                12 // Select between end
                -> {
                    if (galleryAdapter.itemCount < 4)
                        closeTutorialWithError()

                    // If the user is lazy select for them
                    if (galleryAdapter.selectedItemCount < 1)
                        programmaticLongClick(1)

                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText2))

                    setTutorialHomeView(tutorial, true)
                    val view = gridview.getChildAt(3)    //WTF index is backwards.
                    if (view != null)
                        tutorial.setShowcase(MorphViewTarget(view, galleryItemMargin), true)
                    else
                        tutorial.setShowcase(Target.NONE, false)    //TODO: User set an empty folder, somehow???
                }
                13 // Select between feedback
                -> {
                    // If the user is lazy select for them
                    if (galleryAdapter.selectedItemCount < 2)
                        programmaticLongClick(3)

                    tutorial.setContentText(getString(R.string.tutorialSelectBetweenText3))
                    setTutorialTitleView(tutorial, true)
                }
                14 // Rename
                -> {
                    if (!isContextModeActive)
                        startContextMode()

                    tutorial.setContentTitle(getString(R.string.tutorialRenameTitle))
                    tutorial.setContentText(getString(R.string.tutorialRenameText))
                    setTutorialActionView(tutorial, R.id.contextRename, true)
                }
                15 // Move
                -> {
                    if (!isContextModeActive)
                        startContextMode()

                    tutorial.setContentTitle(getString(R.string.tutorialMoveTitle))
                    tutorial.setContentText(getString(R.string.tutorialMoveText))
                    setTutorialActionView(tutorial, R.id.contextCopy, true)
                }
                16 // Export
                -> {
                    if (!isContextModeActive)
                        startContextMode()

                    tutorial.setContentTitle(getString(R.string.tutorialExportTitle))
                    tutorial.setContentText(getString(R.string.tutorialExportText))
                    setTutorialActionView(tutorial, R.id.contextSaveAs, true)
                }
                17 // Share (can't figure out how to address the share button
                -> {
                    //					if (!inActionMode)
                    //						startContextualActionBar();
                    //
                    //                    tutorial.setContentTitle(getString(R.string.tutorialShareTitle));
                    //                    tutorial.setContentText(getString(R.string.tutorialShareText));
                    //                    setTutorialShareView(true);
                    //					setTutorialActionView(R.id.contextShare, true);
                    //                    break;
                    tutorialStage++
                    endContextMode()

                    tutorial.setContentTitle(getString(R.string.tutorialRecycleTitle))
                    tutorial.setContentText(getString(R.string.tutorialRecycleText))
                    setTutorialActionView(tutorial, R.id.menu_recycle, true)
                }
                18 // Recycle
                -> {
                    endContextMode()
                    tutorial.setContentTitle(getString(R.string.tutorialRecycleTitle))
                    tutorial.setContentText(getString(R.string.tutorialRecycleText))
                    setTutorialActionView(tutorial, R.id.menu_recycle, true)
                }
                19 // Actionbar help
                -> {
                    tutorial.setContentTitle(getString(R.string.tutorialActionbarHelpTitle))
                    tutorial.setContentText(getString(R.string.tutorialActionbarHelpText))
                    setTutorialActionView(tutorial, R.id.menu_selectAll, true)
                }
                else // We're done
                -> {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            tutorialStage++
        }
    }

    /**
     * ballpark home (filter) location
     */
    private fun setTutorialHomeView(tutorial: ShowcaseView, animate: Boolean) {
        val center = actionItemWidth.toInt() / 2
        val home = PointTarget(center, center, actionItemWidth)  //touch are is 48x48 dp
        tutorial.setShowcase(home, animate)
    }

    /**
     * ballpark title location
     */
    private fun setTutorialTitleView(tutorial: ShowcaseView, animate: Boolean) {
        val center = actionItemWidth.toInt() / 2

        // We guestimate 2.5* wider, so 5* wider to center
        val title = PointTarget(5 * center, center, (3 * center).toFloat())
        tutorial.setShowcase(title, animate)
    }

    /**
     * Showcase item or overflow if it doesn't exist
     * @param itemId menu id
     * *
     * @param animate Animate the showcase from the previous spot.  Recommend FALSE if previous showcase was NONE
     */
    private fun setTutorialActionView(tutorial: ShowcaseView, itemId: Int, animate: Boolean) {
        val target: Target
        val itemView = findViewById<View>(itemId)
        target = if (itemView == null) {
            //List of all mToolbar items, assuming last is overflow
            val views = galleryToolbar.touchables
            MorphViewTarget(views[views.size - 1]) //overflow
        } else {
            MorphViewTarget(itemView)
        }

        tutorial.setShowcase(target, animate)
    }
}
