package com.sigil.app.data

import com.sigil.app.data.model.BlockType
import com.sigil.app.data.model.NoteBlock
import com.sigil.app.data.model.Segment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-rolled JSON (de)serialization for a note's block list, using org.json (bundled with
 * Android, no extra dependency) since Room needs a flat column, not a nested object graph.
 */
object NoteBlockJson {

    fun encode(blocks: List<NoteBlock>): String {
        val arr = JSONArray()
        for (b in blocks) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("type", b.type.name)
            obj.put("done", b.done)
            val segs = JSONArray()
            for (s in b.segments) {
                val so = JSONObject()
                when (s) {
                    is Segment.Text -> {
                        so.put("kind", "text")
                        so.put("value", s.value)
                    }
                    is Segment.Seal -> {
                        so.put("kind", "seal")
                        so.put("secretId", s.secretId)
                        so.put("label", s.label)
                    }
                }
                segs.put(so)
            }
            obj.put("segments", segs)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(json: String): List<NoteBlock> {
        if (json.isBlank()) return listOf(NoteBlock(id = "b0"))
        val arr = JSONArray(json)
        val blocks = ArrayList<NoteBlock>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val segsArr = obj.getJSONArray("segments")
            val segments = ArrayList<Segment>(segsArr.length())
            for (j in 0 until segsArr.length()) {
                val so = segsArr.getJSONObject(j)
                segments.add(
                    when (so.getString("kind")) {
                        "seal" -> Segment.Seal(so.getString("secretId"), so.getString("label"))
                        else -> Segment.Text(so.getString("value"))
                    },
                )
            }
            blocks.add(
                NoteBlock(
                    id = obj.getString("id"),
                    type = runCatching { BlockType.valueOf(obj.getString("type")) }.getOrDefault(BlockType.PARAGRAPH),
                    done = obj.optBoolean("done", false),
                    segments = segments.ifEmpty { listOf(Segment.Text("")) },
                ),
            )
        }
        return blocks.ifEmpty { listOf(NoteBlock(id = "b0")) }
    }
}
