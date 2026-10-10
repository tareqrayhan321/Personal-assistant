package com.personalmentor.app.domain.computer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudComputerUrlTest {
    @Test fun httpsIsAcceptedAndTrailingSlashRemoved() = assertEquals("https://box.example:8443/cc", CloudComputerUrl.normalize(" https://box.example:8443/cc/ "))
    @Test fun plainHttpIsRejected() = assertNull(CloudComputerUrl.normalize("http://box.example"))
    @Test fun localDevelopmentHostsMayUseHttp() {
        assertEquals("http://10.0.2.2:8787", CloudComputerUrl.normalize("http://10.0.2.2:8787"))
        assertEquals("http://localhost:8787", CloudComputerUrl.normalize("http://localhost:8787/"))
    }
    @Test fun junkIsRejected() {
        assertNull(CloudComputerUrl.normalize("https://"))
        assertNull(CloudComputerUrl.normalize("box.example"))
        assertNull(CloudComputerUrl.normalize("https://box.example/a b"))
        assertNull(CloudComputerUrl.normalize("https://box.example?x=1"))
    }
}
