package org.linphone.utils

object UserSession {
    var accountType: String? = null
    var supportNumber: String? = null
    var authorizedSipDomain: String? = null
    var phoneNumb: String? = null

    fun isClient(): Boolean = accountType == "client"
}
