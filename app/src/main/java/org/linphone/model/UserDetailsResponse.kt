package org.linphone.model

data class UserDetailsResponse(
    val account_type: String,
    val authorized_sip_domain: String,
    val username: String,
    val password: String,
    val phone_number: String,
    val support_number: String
)
