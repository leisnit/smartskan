package com.example.smartskan.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A tiny persisted list: held in memory as a StateFlow, written to a JSON file in the background. */
class JsonListStore<T>(
    private val file: File,
    private val scope: CoroutineScope,
    private val encode: (T) -> JSONObject,
    private val decode: (JSONObject) -> T,
) {
    private val writeLock = Mutex()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    fun update(transform: (List<T>) -> List<T>) {
        _items.update(transform)
        scope.launch {
            // Always write the latest state, so out-of-order launches can't persist stale data.
            writeLock.withLock {
                val array = JSONArray()
                _items.value.forEach { array.put(encode(it)) }
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(array.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(array.toString())
                    tmp.delete()
                }
            }
        }
    }

    private fun load(): List<T> = try {
        if (!file.exists()) emptyList() else {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                runCatching { decode(array.getJSONObject(i)) }.getOrNull()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
