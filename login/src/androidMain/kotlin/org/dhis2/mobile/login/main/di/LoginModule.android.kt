package org.dhis2.mobile.login.main.di

import org.dhis2.mobile.login.accounts.data.repository.AccountRepository
import org.dhis2.mobile.login.accounts.data.repository.AccountRepositoryImpl
import org.dhis2.mobile.login.accounts.ui.viewmodel.AccountsViewModel
import org.dhis2.mobile.login.authentication.OpenIdController
import org.dhis2.mobile.login.authentication.OpenIdControllerImpl
import org.dhis2.mobile.login.main.data.LoginRepository
import org.dhis2.mobile.login.main.data.LoginRepositoryImpl
import org.dhis2.mobile.login.main.ui.state.OidcInfo
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

internal actual val accountModule =
    module {
        factory<AccountRepository> {
            AccountRepositoryImpl(
                d2 = get(),
                preferenceProvider = get(),
                context = get(),
                isTrainingFlavor = getProperty("isTrainingFlavor", false),
            )
        }

        single<OpenIdController> { OpenIdControllerImpl() }

        single {
            when (getProperty("openIdType", "")) {
                "token" ->
                    OidcInfo.Token(
                        server = getProperty("openIdServer", ""),
                        loginLabel = getPropertyOrNull("openIdButtonText"),
                        clientId = getProperty("openIdClient", ""),
                        redirectUri = getProperty("openIdRedirectUri", ""),
                        authorizationUrl = getProperty("openIdAuthorizationUrl", ""),
                        tokenUrl = getProperty("openIdTokenUrl", ""),
                        prompt = getPropertyOrNull<String>("openIdPrompt")?.takeIf { it.isNotEmpty() },
                    )

                else ->
                    OidcInfo.Discovery(
                        server = getProperty("openIdServer", ""),
                        loginButtonText = getPropertyOrNull("openIdButtonText"),
                        clientId = getProperty("openIdClient", ""),
                        redirectUri = getProperty("openIdRedirectUri", ""),
                        discoveryUri = getProperty("openIdDiscoveryUri", ""),
                        prompt = getPropertyOrNull<String>("openIdPrompt")?.takeIf { it.isNotEmpty() },
                    )
            }
        }

        factory<LoginRepository> { _ ->
            LoginRepositoryImpl(
                d2 = get(),
                authenticator = get(),
                cryptographyManager = get(),
                preferences = get(),
                d2ErrorMessageProvider = get(),
                crashReportController = get(),
                analyticActions = get(),
                openIdController = get(),
                dispatcher = get(),
                domainErrorMapper = get(),
            )
        }

        viewModel { params ->
            AccountsViewModel(
                navigator = get(),
                repository = get { parametersOf(params.get()) },
            )
        }
    }
