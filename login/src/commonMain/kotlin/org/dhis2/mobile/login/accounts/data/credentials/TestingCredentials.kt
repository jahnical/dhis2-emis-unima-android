package org.dhis2.mobile.login.accounts.data.credentials

data class TestingCredentials(
    val server: String,
    var username: String,
    var password: String,
)

val defaultTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://project.ccdev.org/emis",
            username = "",
            password = "",
        ),
        TestingCredentials(
            server = "https://project.ccdev.org/emis42",
            username = "",
            password = "",
        ),
    )

val trainingTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://play.dhis2.org/demo",
            username = "android",
            password = "Android123",
        ),
    )
