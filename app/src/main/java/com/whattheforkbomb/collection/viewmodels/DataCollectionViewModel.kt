package com.whattheforkbomb.collection.viewmodels

import androidx.lifecycle.ViewModel
import com.whattheforkbomb.collection.services.DataCollectionService
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

class DataCollectionViewModel : ViewModel() {

    private lateinit var _dataCollectionService: DataCollectionService

    lateinit var rootDir: Path

    var dataCollectionService: DataCollectionService
        get() = _dataCollectionService
        set(value) {
            _dataCollectionService = value
        }
}
