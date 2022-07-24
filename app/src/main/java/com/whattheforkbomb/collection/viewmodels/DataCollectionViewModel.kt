package com.whattheforkbomb.collection.viewmodels

import androidx.lifecycle.ViewModel
import com.whattheforkbomb.collection.services.DataCollectionService

class DataCollectionViewModel : ViewModel() {

    private lateinit var _dataCollectionService: DataCollectionService

    var dataCollectionService: DataCollectionService
        get() = _dataCollectionService
        set(value) {
            _dataCollectionService = value
        }
}