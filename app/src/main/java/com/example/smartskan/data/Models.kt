package com.example.smartskan.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScannedDoc(
    val id: String,
    val name: String,
    val pageCount: Int,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("pageCount", pageCount).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = ScannedDoc(
            id = o.getString("id"),
            name = o.getString("name"),
            pageCount = o.optInt("pageCount", 1),
            createdAt = o.getLong("createdAt"),
        )
    }
}

data class ScannedCode(
    val id: String,
    val value: String,
    val format: String,
    val type: String,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("value", value).put("format", format).put("type", type).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = ScannedCode(
            id = o.getString("id"),
            value = o.getString("value"),
            format = o.optString("format"),
            type = o.optString("type"),
            createdAt = o.getLong("createdAt"),
        )
    }
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amountCents: Long,
    val category: String = Categories.OTHER,
    val date: Long = System.currentTimeMillis(),
    val barcode: String? = null,
    val source: String = SOURCE_MANUAL,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("amountCents", amountCents).put("category", category)
        .put("date", date).put("barcode", barcode ?: "").put("source", source)

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_RECEIPT = "receipt"
        const val SOURCE_PRODUCT = "product"

        fun fromJson(o: JSONObject) = Expense(
            id = o.getString("id"),
            title = o.getString("title"),
            amountCents = o.getLong("amountCents"),
            category = o.optString("category", Categories.OTHER),
            date = o.getLong("date"),
            barcode = o.optString("barcode").ifEmpty { null },
            source = o.optString("source", SOURCE_MANUAL),
        )
    }
}

/**
 * A hand-drawn signature, normalised so its width is 1.0: x in 0..1, y in 0..[aspect].
 * Each stroke is interleaved x,y pairs. [strokeWidth] is also relative to the width.
 */
class Signature(val strokes: List<FloatArray>, val aspect: Float, val strokeWidth: Float) {
    fun toJson(): JSONObject = JSONObject()
        .put("aspect", aspect.toDouble())
        .put("strokeWidth", strokeWidth.toDouble())
        .put("strokes", JSONArray().apply {
            strokes.forEach { s -> put(JSONArray().apply { s.forEach { put(it.toDouble()) } }) }
        })

    companion object {
        fun fromJson(o: JSONObject): Signature {
            val arr = o.getJSONArray("strokes")
            val strokes = (0 until arr.length()).map { i ->
                val s = arr.getJSONArray(i)
                FloatArray(s.length()) { s.getDouble(it).toFloat() }
            }
            return Signature(strokes, o.getDouble("aspect").toFloat(), o.getDouble("strokeWidth").toFloat())
        }
    }
}

object Categories {
    const val GROCERIES = "Groceries"
    const val OTHER = "Other"
    val all = listOf(GROCERIES, "Dining", "Transport", "Shopping", "Bills", "Health", "Entertainment", OTHER)
}
