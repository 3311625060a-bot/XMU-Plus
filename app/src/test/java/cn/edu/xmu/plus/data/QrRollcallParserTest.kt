package cn.edu.xmu.plus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class QrRollcallParserTest {

    @Test
    fun parse_directJsonQr_extractsRollcallAndData() {
        val raw = """{"rollcallId":90214,"data":"abc~123!xyz","courseId":107546}"""
        val parsed = QrRollcallParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(90214L, parsed!!.rollcallId)
        assertEquals("abc~123!xyz", parsed.data)
        assertEquals(107546L, parsed.courseId)
    }

    @Test
    fun parse_jumperUrlWithUnderscoreP_extractsFields() {
        val json = """{"rollcallId":"88123","data":"token_xyz","courseId":55201}"""
        val encoded = URLEncoder.encode(json, "UTF-8")
        val url = "https://c-mobile.xmu.edu.cn/scanner-jumper?_p=$encoded"
        val parsed = QrRollcallParser.parse(url)
        assertNotNull(parsed)
        assertEquals(88123L, parsed!!.rollcallId)
        assertEquals("token_xyz", parsed.data)
        assertEquals(55201L, parsed.courseId)
    }

    @Test
    fun parse_compactPFormatWithBase36NumbersAndEscapes_decodesAccurately() {
        // shortKeys: "0" -> courseId, "3" -> data, "4" -> rollcallId
        // \u0010 + base36: 90214.toString(36) = "1xm2", 107546.toString(36) = "2azm"
        val rollcallB36 = 90214L.toString(36)
        val courseB36 = 107546L.toString(36)
        val escapedData = "tk\u001F123\u001Eok" // unescapes to "tk~123!ok"
        val compact = "0~\u0010$courseB36!4~\u0010$rollcallB36!3~$escapedData"
        val url = "https://c-mobile.xmu.edu.cn/j?p=${URLEncoder.encode(compact, "UTF-8")}"

        val parsed = QrRollcallParser.parse(url)
        assertNotNull(parsed)
        assertEquals(90214L, parsed!!.rollcallId)
        assertEquals(107546L, parsed.courseId)
        assertEquals("tk~123!ok", parsed.data)
    }

    @Test
    fun parse_unrelatedOrInvalidQr_returnsNull() {
        assertNull(QrRollcallParser.parse(""))
        assertNull(QrRollcallParser.parse("https://example.com/hello"))
        assertNull(QrRollcallParser.parse("""{"courseId":123}"""))
    }
}
