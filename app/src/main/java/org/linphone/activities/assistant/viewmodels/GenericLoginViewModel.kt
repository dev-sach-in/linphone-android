/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class GenericLoginViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GenericLoginViewModel(accountCreator) as T
    }
}

class GenericLoginViewModel(private val accountCreator: AccountCreator) : ViewModel() {
    val username = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val domain = MutableLiveData<String>()

    val displayName = MutableLiveData<String>()

    val transport = MutableLiveData<TransportType>()

    val loginEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val leaveAssistantEvent = MutableLiveData<Event<Boolean>>()

    val invalidCredentialsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var accountToCheck: Account? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == accountToCheck) {
                Log.i("[Assistant] [Generic Login] Registration state is $state: $message")
                if (state == RegistrationState.Ok) {
                    waitForServerAnswer.value = false
                    leaveAssistantEvent.value = Event(true)
                    core.removeListener(this)
                } else if (state == RegistrationState.Failed) {
                    waitForServerAnswer.value = false
                    invalidCredentialsEvent.value = Event(true)
                    core.removeListener(this)
                }
            }
        }
    }

    init {
        transport.value = TransportType.Udp
        domain.value = "pbx2.telxio.com.sg:4144"

        loginEnabled.value = false
        loginEnabled.addSource(username) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(domain) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    fun setTransport(transportType: TransportType) {
        transport.value = transportType
    }

    fun removeInvalidProxyConfig() {
        val account = accountToCheck
        account ?: return

        val core = coreContext.core
        val authInfo = account.findAuthInfo()
        if (authInfo != null) core.removeAuthInfo(authInfo)
        core.removeAccount(account)
        accountToCheck = null

        // Make sure there is a valid default account
        val accounts = core.accountList
        if (accounts.isNotEmpty() && core.defaultAccount == null) {
            core.defaultAccount = accounts.first()
            core.refreshRegisters()
        }
    }

    fun continueEvenIfInvalidCredentials() {
        leaveAssistantEvent.value = Event(true)
    }

    fun createAccountAndAuthInfo() {
        waitForServerAnswer.value = true
        coreContext.core.addListener(coreListener)

        accountCreator.username = username.value
        accountCreator.password = password.value
        accountCreator.domain = domain.value
        accountCreator.displayName = displayName.value
        accountCreator.transport = transport.value

        val account = accountCreator.createAccountInCore()
        accountToCheck = account

        if (account == null) {
            Log.e("[Assistant] [Generic Login] Account creator couldn't create account")
            coreContext.core.removeListener(coreListener)
            onErrorEvent.value = Event("Error: Failed to create account object")
            waitForServerAnswer.value = false
            return
        }

        Log.i("[Assistant] [Generic Login] Account created")
    }

    private fun isLoginButtonEnabled(): Boolean {
        return username.value.orEmpty().isNotEmpty() &&
            domain.value.orEmpty().isNotEmpty() &&
            password.value.orEmpty().isNotEmpty()
    }
}
