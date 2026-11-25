package cn.edu.xmu.plus.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class CasCryptoTest {

    @Test
    fun encryptPassword_producesValidBase64WithExpectedLengthAndCharacters() {
        val salt = "kEv7VdceUV5b4iWc"
        val raw = "XmuTestPass!123"
        val encrypted = CasCrypto.encryptPassword(raw, salt, SecureRandom())
        val decoded = Base64.getDecoder().decode(encrypted)

        // 64 random prefix + 15 char password = 79 bytes -> padded to 80 bytes (5 AES blocks)
        assertEquals(80, decoded.size)
        val prefix = CasCrypto.randomString(64)
        assertEquals(64, prefix.length)
        assertTrue(prefix.all { it in CasCrypto.AES_CHARS })
    }

    @Test
    fun parseCasPwdForm_isolatesPwdFromIdFromOtherForms() {
        val html = """
            <html><body>
              <form id="loginFromId" action="/authserver/login">
                <input type="hidden" id="cllt" name="cllt" value="fidoLogin" />
                <input type="hidden" id="execution" name="execution" value="wrong1" />
              </form>
              <form id="phoneFromId" action="/authserver/login">
                <input type="hidden" id="cllt" name="cllt" value="dynamicLogin" />
                <input type="hidden" id="execution" name="execution" value="wrong2" />
              </form>
              <form id="pwdFromId" action="/authserver/login" method="post">
                <input type="hidden" id="_eventId" name="_eventId" value="submit" />
                <input type="hidden" id="cllt" name="cllt" value="userNameLogin" />
                <input type="hidden" id="dllt" name="dllt" value="generalLogin" />
                <input type="hidden" name="lt" id="lt" value="" />
                <input type="hidden" id="pwdEncryptSalt" value="kEv7VdceUV5b4iWc" />
                <input type="hidden" id="execution" name="execution" value="e1s1" />
              </form>
            </body></html>
        """.trimIndent()

        val parsed = XmuAuthClient.parseCasPwdForm(html)
        assertNotNull(parsed)
        assertEquals("e1s1", parsed!!.execution)
        assertEquals("kEv7VdceUV5b4iWc", parsed.pwdEncryptSalt)
        assertEquals("userNameLogin", parsed.cllt)
        assertEquals("generalLogin", parsed.dllt)
        assertEquals("submit", parsed.eventId)
    }

    @Test
    fun parseCasPwdForm_handlesMobileCasTemplateWithDuplicateLoginFromId() {
        val mobileHtml = """
            <html><body>
              <form method="post" id="loginFromId" action="/authserver/login">
                <input type="hidden" id="cllt" name="cllt" value="dynamicLogin" />
                <input type="hidden" name="execution" value="wrongMob" />
              </form>
              <form method="post" id="loginFromId" action="/authserver/login">
                <input type="text" id="username" name="username" value="" />
                <input type="password" id="password" name="userPassword" />
                <input type="hidden" id="saltPassword" name="password">
                <input type="hidden" name="_eventId" value="submit" />
                <input type="hidden" id="lt" name="lt" value="">
                <input type="hidden" id="cllt" name="cllt" value="userNameLogin">
                <input type="hidden" id="dllt" name="dllt" value="generalLogin">
                <input type="hidden" id="pwdEncryptSalt" value="mObIlEsAlT123456" />
                <input type="hidden" id="execution" name="execution" value="e2s1" />
              </form>
            </body></html>
        """.trimIndent()

        val parsed = XmuAuthClient.parseCasPwdForm(mobileHtml)
        assertNotNull(parsed)
        assertEquals("e2s1", parsed!!.execution)
        assertEquals("mObIlEsAlT123456", parsed.pwdEncryptSalt)
        assertEquals("userNameLogin", parsed.cllt)
    }
}
