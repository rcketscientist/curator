package com.anthonymandra.rawdroid.ui

import android.net.Uri
import com.anthonymandra.rawdroid.data.MetadataTest
import com.davemorrissey.labs.subscaleview.ImageSource

class RawImageSource constructor(val source: MetadataTest)
    : ImageSource(Uri.parse(source.uri))
