package cn.edu.xmu.plus.data

import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {

    @Test
    fun openSourceLicenses_allEntriesHaveRequiredMetadataAndHttpsUrls() {
        val items = OpenSourceLicenses.items
        assertTrue(items.size >= 5)
        items.forEach { notice ->
            assertTrue("Missing name", notice.name.isNotBlank())
            assertTrue("Missing author for ${notice.name}", notice.author.isNotBlank())
            assertTrue("Missing version for ${notice.name}", notice.version.isNotBlank())
            assertTrue("Missing license for ${notice.name}", notice.license.isNotBlank())
            assertTrue("Invalid URL for ${notice.name}", notice.url.startsWith("https://"))
            assertTrue("Missing description for ${notice.name}", notice.description.isNotBlank())
        }
    }
}
