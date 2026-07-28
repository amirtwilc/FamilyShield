package com.familyshield.mobile.parent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PhoneIntentManifestTest {
    @Test
    fun `manifest exposes dial and view telephone handlers`() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val intents = document.getElementsByTagName("intent")
        val actionSchemes = buildSet {
            for (index in 0 until intents.length) {
                val intent = intents.item(index)
                val children = intent.childNodes
                var action: String? = null
                var scheme: String? = null
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex)
                    when (child.nodeName) {
                        "action" -> action = child.attributes?.getNamedItem("android:name")?.nodeValue
                        "data" -> scheme = child.attributes?.getNamedItem("android:scheme")?.nodeValue
                    }
                }
                if (action != null && scheme != null) add(action to scheme)
            }
        }

        assertTrue("ACTION_DIAL tel query is missing", "android.intent.action.DIAL" to "tel" in actionSchemes)
        assertTrue("ACTION_VIEW tel fallback query is missing", "android.intent.action.VIEW" to "tel" in actionSchemes)
    }
}
