package com.sigil.app.ipc

/** The diary line built from Kasane's and Vow's status rows. Numbers only; no momentum figure is ever shown. */
object DiaryRecap {
    fun lines(kasane: Map<String, String>?, vow: Map<String, String>?): List<String> {
        val out = ArrayList<String>()
        kasane?.let { r ->
            val kept = r["rulesKept"]
            val total = r["rulesTotal"]
            if (kept != null && total != null) out += "Rules kept: $kept of $total"
        }
        vow?.get("reclaimedMinutes")?.let { out += "Time reclaimed: $it min" }
        return out
    }
}
