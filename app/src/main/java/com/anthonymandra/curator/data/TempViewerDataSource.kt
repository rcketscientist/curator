package com.anthonymandra.curator.data

import androidx.paging.PositionalDataSource

/**
 * Simple [PositionalDataSource] that wraps a [List] of [ImageInfo] for use with share intents
 */
class TempViewerDataSource(val images: List<ImageInfo>) : PositionalDataSource<ImageInfo>() {

	override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<ImageInfo>) {
		callback.onResult(images)
	}

	override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<ImageInfo>) {
		callback.onResult(images, params.requestedStartPosition, images.count())
	}
}