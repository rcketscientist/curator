package com.anthonymandra.rawdroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.FileUtil;
import com.github.amlcurran.showcaseview.MorphShowcaseDrawer;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.MorphViewTarget;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.Target;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class TutorialActivity extends GalleryActivity
{
	int tutorialStage;
	private ShowcaseView tutorial;
	private File tutorialDirectory;

	/**
	 * Actual pixel width of the 48dp action item touch box.
	 */
	private float actionItemWidth;

	/**
	 * Margin applied only to gallery items in addition to {@link #showcaseItemMargin}.
	 */
	private float galleryItemMargin;

	/**
	 * Margin applied to all showcase items.
	 */
	private float showcaseItemMargin;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		actionItemWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
		showcaseItemMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
		galleryItemMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());

		tutorialStage = 0;
		tutorialDirectory = FileUtil.getDiskCacheDir(this, "tutorial");
		if (!tutorialDirectory.exists())
		{
			if(!tutorialDirectory.mkdir())
				finish();
		}

		//generate some example images
		File f1 = new File(tutorialDirectory, "Image1.jpg");
		File f2 = new File(tutorialDirectory, "Image2.jpg");
		File f3 = new File(tutorialDirectory, "Image3.jpg");
		File f4 = new File(tutorialDirectory, "Image4.jpg");
		File f5 = new File(tutorialDirectory, "Image5.jpg");

		try(
				FileOutputStream one = new FileOutputStream(f1);
				FileOutputStream two = new FileOutputStream(f2);
				FileOutputStream three = new FileOutputStream(f3);
				FileOutputStream four = new FileOutputStream(f4);
				FileOutputStream five = new FileOutputStream(f5))
		{

			BitmapFactory.decodeResource(getResources(), R.drawable.tutorial1).compress(Bitmap.CompressFormat.JPEG, 100, one);
			BitmapFactory.decodeResource(getResources(), R.drawable.tutorial2).compress(Bitmap.CompressFormat.JPEG, 100, two);
			BitmapFactory.decodeResource(getResources(), R.drawable.tutorial3).compress(Bitmap.CompressFormat.JPEG, 100, three);
			BitmapFactory.decodeResource(getResources(), R.drawable.tutorial4).compress(Bitmap.CompressFormat.JPEG, 100, four);
			BitmapFactory.decodeResource(getResources(), R.drawable.tutorial5).compress(Bitmap.CompressFormat.JPEG, 100, five);

			addDatabaseReference(Uri.fromFile(f1));
			addDatabaseReference(Uri.fromFile(f2));
			addDatabaseReference(Uri.fromFile(f3));
			addDatabaseReference(Uri.fromFile(f4));
			addDatabaseReference(Uri.fromFile(f5));
		}
		catch (IOException e)
		{
			Toast.makeText(this, "Unable to open tutorial examples.  Please skip file selection.", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		updateMetaLoader(null,
				Meta.URI + " LIKE ?",
				new String[] {"%"+tutorialDirectory.getName()+"%"}, null);

		tutorial = new ShowcaseView.Builder(this)//, true)
				.setShowcaseDrawer(new MorphShowcaseDrawer(getResources(), showcaseItemMargin))
				.setContentTitle(R.string.tutorialWelcomeTitle)
				.setContentText(R.string.tutorialWelcomeText)
				.doNotBlockTouches()
				.setStyle(R.style.CustomShowcaseTheme2)
				.replaceEndButton(R.layout.tutorial_button)
				.setOnClickListener(new TutorialClickListener())
				.build();

		tutorial.setButtonText(getString(R.string.next));
		tutorial.setDetailTextAlignment(Layout.Alignment.ALIGN_OPPOSITE);
		tutorial.setShowcase(Target.NONE, false);
	}

	@Override
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void onDestroy()
	{
		super.onDestroy();

		endContextMode();

		File tutorialDirectory = FileUtil.getDiskCacheDir(this, "tutorial");
		File f1 = new File(tutorialDirectory, "Image1.jpg");
		File f2 = new File(tutorialDirectory, "Image2.jpg");
		File f3 = new File(tutorialDirectory, "Image3.jpg");
		File f4 = new File(tutorialDirectory, "Image4.jpg");
		File f5 = new File(tutorialDirectory, "Image5.jpg");

		removeDatabaseReference(Uri.fromFile(f1));
		removeDatabaseReference(Uri.fromFile(f2));
		removeDatabaseReference(Uri.fromFile(f3));
		removeDatabaseReference(Uri.fromFile(f4));
		removeDatabaseReference(Uri.fromFile(f5));

		f1.delete();
		f2.delete();
		f3.delete();
		f4.delete();
		f5.delete();
		tutorialDirectory.delete();
	}

	private class TutorialClickListener implements View.OnClickListener
	{
		//Note: Don't animate coming from "NoShowcase" it flies in from off screen which is silly.
		View view;
		@Override
		public void onClick(View v)
		{
			switch (tutorialStage)
			{
				case 0: // Connection
					tutorial.setContentTitle(getString(R.string.tutorialConnectTitle));
					tutorial.setContentText(getString(R.string.tutorialConnectText1));
					break;
				case 1: // Connection
					tutorial.setContentText(getString(R.string.tutorialConnectText2));
					break;
				case 2: // Connection
					tutorial.setContentText(getString(R.string.tutorialConnectText3));
					break;
				case 3: // Connection
					tutorial.setContentText(getString(R.string.tutorialConnectText4));
					break;
				case 4: // Connection
					tutorial.setContentText(getString(R.string.tutorialConnectText5));
					break;
				case 5: // Search
					tutorial.setContentText(getString(R.string.tutorialFindImagesText));
					setTutorialActionView(R.id.galleryRefresh, false);
					break;
				case 6: // Long Select
					tutorial.setContentTitle(getString(R.string.tutorialSelectTitle));
					tutorial.setContentText(getString(R.string.tutorialSingleSelectText));
					view = getGalleryView().getChildAt(0);
					if (view != null)
						tutorial.setShowcase(new MorphViewTarget(view, galleryItemMargin), true);
					else
						tutorial.setShowcase(Target.NONE, false);    //TODO: User set an empty folder, somehow???
					break;
				case 7: // Add select
					// If the user is lazy select for them
					if (!isContextModeActive())
						onItemLongClick(getGalleryAdapter(), getGalleryView().getChildAt(0), 0, 0);

					tutorial.setContentText(getString(R.string.tutorialMultiSelectText));
					view = getGalleryView().getChildAt(2);
					if (view != null)
						tutorial.setShowcase(new MorphViewTarget(view, galleryItemMargin), true);
					else
						tutorial.setShowcase(Target.NONE, false);    //TODO: User set an empty folder, somehow???
					break;
				case 8: // Select feedback
					// If the user is lazy select for them
					if (getGalleryAdapter().getSelectedItemCount() < 2)
					{
						onItemLongClick(getGalleryAdapter(), getGalleryView().getChildAt(0), 0, 0);
						onItemClick(getGalleryAdapter(), getGalleryView().getChildAt(2), 2, 2);
					}

					tutorial.setContentText(getString(R.string.tutorialMultiSelectText2));
					setTutorialTitleView(true);
					break;
				case 9: // Select All
					endContextMode();

					tutorial.setContentText(getString(R.string.tutorialSelectAll));
					setTutorialActionView(R.id.menu_selectAll, true);
					break;
				case 10: // Exit Selection
					// If the user is lazy select for them
					if (getGalleryAdapter().getSelectedItemCount() < 1)
						selectAll();

					tutorial.setContentText(getString(R.string.tutorialExitSelectionText));
					setTutorialHomeView(true);
					break;
				case 11: // Select between beginning
					endContextMode();

					tutorial.setContentText(getString(R.string.tutorialSelectBetweenText1));
					view = getGalleryView().getChildAt(1);		//WTF index is backwards.
					if (view != null)
						tutorial.setShowcase(new MorphViewTarget(view, galleryItemMargin), true);
					else
						tutorial.setShowcase(Target.NONE, false);    //TODO: User set an empty folder, somehow???
					break;
				case 12: // Select between end
					// If the user is lazy select for them
					if (getGalleryAdapter().getSelectedItemCount() < 1)
						onItemLongClick(getGalleryAdapter(), getGalleryView().getChildAt(1), 1, 1);

					tutorial.setContentText(getString(R.string.tutorialSelectBetweenText2));

					setTutorialHomeView(true);
					view = getGalleryView().getChildAt(3);	//WTF index is backwards.
					if (view != null)
						tutorial.setShowcase(new MorphViewTarget(view, galleryItemMargin), true);
					else
						tutorial.setShowcase(Target.NONE, false);    //TODO: User set an empty folder, somehow???
					break;
				case 13: // Select between feedback
					// If the user is lazy select for them
					if (getGalleryAdapter().getSelectedItemCount() < 2)
					{
						onItemLongClick(getGalleryAdapter(), getGalleryView().getChildAt(1), 1, 1);
						onItemLongClick(getGalleryAdapter(), getGalleryView().getChildAt(3), 3, 3);
					}

					tutorial.setContentText(getString(R.string.tutorialSelectBetweenText3));
					setTutorialTitleView(true);
					break;
				case 14: // Rename
					if (!isContextModeActive())
						startContextMode();

					tutorial.setContentTitle(getString(R.string.tutorialRenameTitle));
					tutorial.setContentText(getString(R.string.tutorialRenameText));
					setTutorialActionView(R.id.contextRename, true);
					break;
				case 15: // Move
					if (!isContextModeActive())
						startContextMode();

					tutorial.setContentTitle(getString(R.string.tutorialMoveTitle));
					tutorial.setContentText(getString(R.string.tutorialMoveText));
					setTutorialActionView(R.id.contextCopy, true);
					break;
				case 16: // Export
					if (!isContextModeActive())
						startContextMode();

					tutorial.setContentTitle(getString(R.string.tutorialExportTitle));
					tutorial.setContentText(getString(R.string.tutorialExportText));
					setTutorialActionView(R.id.contextSaveAs, true);
					break;
				case 17: // Share (can't figure out how to address the share button
//					if (!inActionMode)
//						startContextualActionBar();
//
//                    tutorial.setContentTitle(getString(R.string.tutorialShareTitle));
//                    tutorial.setContentText(getString(R.string.tutorialShareText));
//                    setTutorialShareView(true);
//					setTutorialActionView(R.id.contextShare, true);
//                    break;
					tutorialStage++;
				case 18: // Recycle
					endContextMode();

					tutorial.setContentTitle(getString(R.string.tutorialRecycleTitle));
					tutorial.setContentText(getString(R.string.tutorialRecycleText));
					setTutorialActionView(R.id.menu_recycle, true);
					break;
				case 19: // Actionbar help
					tutorial.setContentTitle(getString(R.string.tutorialActionbarHelpTitle));
					tutorial.setContentText(getString(R.string.tutorialActionbarHelpText));
					setTutorialActionView(R.id.menu_selectAll, true);
					break;
				default: // We're done
					finish();
					break;
			}
			tutorialStage++;
		}
	}

	/**
	 * ballpark home (filter) location
	 */
	private void setTutorialHomeView(boolean animate)
	{
		int center = (int) actionItemWidth / 2;
		PointTarget home = new PointTarget(center,center, actionItemWidth);  //touch are is 48x48 dp
		tutorial.setShowcase(home, animate);
	}

	/**
	 * ballpark title location
	 */
	private void setTutorialTitleView(boolean animate)
	{
		int center = (int) actionItemWidth / 2;

		// We guestimate 2.5* wider, so 5* wider to center
		PointTarget title = new PointTarget(5 * center, center, 3 * center );
		tutorial.setShowcase(title, animate);
	}

	/**
	 * Showcase item or overflow if it doesn't exist
	 * @param itemId menu id
	 * @param animate Animate the showcase from the previous spot.  Recommend FALSE if previous showcase was NONE
	 */
	private void setTutorialActionView(int itemId, boolean animate)
	{
		Target target;
		View itemView = findViewById(itemId);
		if (itemView == null)
		{
			//List of all mToolbar items, assuming last is overflow
			List<View> views = getToolbar().getTouchables();
			target = new MorphViewTarget(views.get(views.size()-1)); //overflow
		}
		else
		{
			target = new MorphViewTarget(itemView);
		}

		tutorial.setShowcase(target, animate);
	}
}
