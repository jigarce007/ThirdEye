/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.network.RetrofitBuilder
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import java.util.Locale

class ThirdPartySipAccountLoginViewModel
@UiThread
constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Third Party SIP Account Login ViewModel]"
    }

    val username = MutableLiveData<String>()
    val authId = MutableLiveData<String>()
    val password = MutableLiveData<String>()
    val domain = MutableLiveData<String>()
    val displayName = MutableLiveData<String>()
    val transport = MutableLiveData<String>()
    val internationalPrefix = MutableLiveData<String>()
    val internationalPrefixIsoCountryCode = MutableLiveData<String>()
    val showPassword = MutableLiveData<Boolean>()
    val expandAdvancedSettings = MutableLiveData<Boolean>()
    val outboundProxy = MutableLiveData<String>()
    val loginEnabled = MediatorLiveData<Boolean>()
    val registrationInProgress = MutableLiveData<Boolean>()

    val accountLoggedInEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val accountLoginErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val defaultTransportIndexEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    val availableTransports = arrayListOf<String>()

    private lateinit var newlyCreatedAuthInfo: AuthInfo
    private lateinit var newlyCreatedAccount: Account

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == newlyCreatedAccount) {
                Log.i("$TAG Newly created account registration state is [$state] ($message)")

                if (state == RegistrationState.Ok) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)
                    core.defaultAccount = newlyCreatedAccount
                    accountLoggedInEvent.postValue(Event(true))
                } else if (state == RegistrationState.Failed) {
                    registrationInProgress.postValue(false)
                    core.removeListener(this)

                    val error = when (account.error) {
                        Reason.Forbidden -> {
                            AppUtils.getString(R.string.assistant_account_login_forbidden_error)
                        }
                        else -> {
                            AppUtils.getFormattedString(
                                R.string.assistant_account_login_error,
                                account.error.toString()
                            )
                        }
                    }
                    accountLoginErrorEvent.postValue(Event(error))
                    Log.e("$TAG Account failed to REGISTER [$message], removing it")
                    core.removeAuthInfo(newlyCreatedAuthInfo)
                    core.removeAccount(newlyCreatedAccount)
                }
            }
        }
    }

    init {
        showPassword.value = false
        expandAdvancedSettings.value = false
        registrationInProgress.value = false

        loginEnabled.addSource(username) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }

        availableTransports.add(TransportType.Udp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tcp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tls.name.uppercase(Locale.getDefault()))
        defaultTransportIndexEvent.postValue(Event(0))

        coreContext.postOnCoreThread {
            domain.postValue(corePreferences.thirdPartySipAccountDefaultDomain)
            val defaultTransport = corePreferences.thirdPartySipAccountDefaultTransport.uppercase(
                Locale.getDefault()
            )
            val index = if (defaultTransport.isNotEmpty()) {
                availableTransports.indexOf(defaultTransport)
            } else {
                availableTransports.size - 1
            }
            defaultTransportIndexEvent.postValue(Event(index))
        }
    }

    @UiThread
    fun login() {
        val email = username.value.orEmpty().trim()
        val pass = password.value.orEmpty().trim()

        if (email.isEmpty() || pass.isEmpty()) return

        registrationInProgress.postValue(true)
        coreContext.postOnCoreThread { core ->
            core.loadConfigFromXml(corePreferences.thirdPartyDefaultValuesPath)
            viewModelScope.launch {
                try {
                    val authResponse = RetrofitBuilder.apiService.login(email, pass)
                    val userDetails = RetrofitBuilder.apiService.getUserDetails(authResponse.uid)

                    val apiUsername = userDetails.username
                    val apiPassword = userDetails.password
                    val apiDomain = userDetails.authorized_sip_domain

                    Log.i("$TAG Parsed username is [$apiUsername] and domain [$apiDomain]")

                    newlyCreatedAuthInfo = Factory.instance().createAuthInfo(
                        apiUsername, "", apiPassword, null, null, apiDomain
                    )
                    core.addAuthInfo(newlyCreatedAuthInfo)

                    val accountParams = core.createAccountParams()
                    val identityAddress = Factory.instance().createAddress("sip:$apiUsername@$apiDomain")
                    accountParams.identityAddress = identityAddress

                    val serverAddress = Factory.instance().createAddress("sip:$apiDomain")
                    serverAddress?.transport = TransportType.Tls
                    accountParams.serverAddress = serverAddress

                    newlyCreatedAccount = core.createAccount(accountParams)
                    core.addListener(coreListener)
                    core.addAccount(newlyCreatedAccount)
                } catch (e: Exception) {
                    accountLoginErrorEvent.postValue(Event("Login failed: ${e.localizedMessage}"))
                }
            }
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isLoginButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() && password.value.orEmpty().isNotEmpty()
    }
}
