package org.dhis2.mobile.login.accounts.data.repository

import android.content.Context
import org.dhis2.mobile.commons.providers.PreferenceProvider
import org.dhis2.mobile.login.accounts.data.credentials.TestingCredentials
import org.dhis2.mobile.login.accounts.data.credentials.defaultTestingCredentials
import org.dhis2.mobile.login.accounts.data.credentials.trainingTestingCredentials
import org.dhis2.mobile.login.accounts.domain.model.AccountModel
import org.dhis2.mobile.login.main.data.PREF_URLS
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.configuration.internal.DatabaseAccount
import org.json.JSONArray

class AccountRepositoryImpl(
    private val d2: D2,
    private val preferenceProvider: PreferenceProvider,
    private val context: Context,
    private val isTrainingFlavor: Boolean,
) : AccountRepository {
    override suspend fun getLoggedInAccounts(): List<AccountModel> =
        d2.userModule().accountManager().getAccounts().map {
            mapDatabaseAccountToAccountModel(it)
        }

    override suspend fun availableServers(): List<String> {
        val rawCredentials =
            if (isTrainingFlavor) {
                loadRawCredentials("training_credentials")
            } else {
                loadRawCredentials("testing_credentials")
            }

        val providedServers =
            when {
                rawCredentials.isNotEmpty() -> rawCredentials
                isTrainingFlavor -> trainingTestingCredentials
                else -> defaultTestingCredentials
            }

        providedServers.forEach {
            preferenceProvider.updateLoginServers(it.server)
        }

        return preferenceProvider
            .getSet(PREF_URLS, HashSet())
            .orEmpty()
            .toList()
    }

    private fun loadRawCredentials(rawResName: String): List<TestingCredentials> =
        try {
            val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
            if (resId != 0) {
                val jsonString =
                    context.resources
                        .openRawResource(resId)
                        .bufferedReader()
                        .use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val credentials = mutableListOf<TestingCredentials>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val serverUrl = obj.optString("server_url", "").trim()
                    if (serverUrl.isNotEmpty()) {
                        credentials.add(
                            TestingCredentials(
                                server = serverUrl,
                                username = obj.optString("user_name", ""),
                                password = obj.optString("user_pass", ""),
                            ),
                        )
                    }
                }
                credentials
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

    override suspend fun getActiveAccount(): AccountModel? =
        d2.userModule().accountManager().getCurrentAccount()?.let {
            mapDatabaseAccountToAccountModel(it)
        }

    private fun mapDatabaseAccountToAccountModel(databaseAccount: DatabaseAccount): AccountModel {
        val oidcProviders = databaseAccount.loginConfig()?.oidcProviders?.firstOrNull()
        val serverName =
            databaseAccount.loginConfig()?.applicationTitle ?: try {
                databaseAccount.serverUrl().substringAfter("://").substringBefore("/")
            } catch (_: Exception) {
                databaseAccount.serverUrl()
            }
        return AccountModel(
            name = databaseAccount.username(),
            serverName = serverName,
            serverUrl = databaseAccount.serverUrl(),
            serverDescription = databaseAccount.loginConfig()?.applicationDescription,
            serverFlag = databaseAccount.loginConfig()?.countryFlag,
            allowRecovery = databaseAccount.loginConfig()?.allowAccountRecovery == true,
            oidcIcon = oidcProviders?.icon,
            oidcLoginText = oidcProviders?.loginText,
            oidcUrl = oidcProviders?.url,
            isOauthEnabled = databaseAccount.loginConfig()?.isOauthEnabled() == true,
        )
    }
}
