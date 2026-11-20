# Tax Enrolment Assignment Frontend

Tax Enrolment Assignment Frontend (TENs) checks which Government Gateway or One Login user IDs are linked to a user's National Insurance number. It assigns the HMRC-PT (Personal Tax Account) enrolment to the right user ID and helps users who have Self Assessment (IR-SA) or MTD IT on a different user ID. Services such as the Personal Tax Account send users here, and TENs returns them to the `redirectUrl` they came from.

## Summary

This service provides:

* the main `/protect-tax-info` journey entry point
* pages for users with more than one user ID, including choosing where to keep Self Assessment access and reporting a user ID they don't recognise
* integration with EACD (via enrolment-store-proxy), tax-enrolments and users-groups-search
* test-only endpoints for creating and deleting user and enrolment data
* support for Government Gateway and One Login test journeys
* redirect validation for the URLs used by downstream services

## Requirements

This service is written in [Scala 3.x](http://www.scala-lang.org/) and [Play 3.x](http://playframework.com/), so needs at least a [JRE 21](http://www.oracle.com/technetwork/java/javase/downloads/index.html) to run.

## How to test the project

### Unit tests

* Run the full unit test suite: `sbt test`
* Run a single spec file: `sbt "testOnly *fileName"`  
  For example: `sbt "testOnly *AccountCheckControllerSpec"`

### Integration tests

* Run the integration test suite: `sbt it/test`

### Journey (acceptance) tests

The UI journey tests live in [tax-enrolment-assignment-journey-tests](https://github.com/hmrc/tax-enrolment-assignment-journey-tests). See that repository's README for its prerequisites (PostgreSQL and MongoDB).

To run them against a local build of this service:

1. Start the TENs services in sm2: `sm2 --start TAX_ENROLMENT_ASSIGNMENT_ALL`
2. Stop the `TAX_ENROLMENT_ASSIGNMENT_FRONTEND` process running in sm2: `sm2 --stop TAX_ENROLMENT_ASSIGNMENT_FRONTEND`
3. In this repository, run the service with the test-only routes: `./run.sh`
4. In the `tax-enrolment-assignment-journey-tests` repository, run: `./run_tests_local.sh`

## Running locally

Start the dependent services in sm2, then stop the sm2 copy of this service so you can run your local build:

sm2 --start TAX_ENROLMENT_ASSIGNMENT_ALL
sm2 --stop TAX_ENROLMENT_ASSIGNMENT_FRONTEND
./run.sh

`run.sh` starts the service on port 7750 with the test-only routes enabled. It is the same as:

`sbt -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes "run 7750"`

To walk the journey, open the test-only user selection page:

`http://localhost:7750/protect-tax-info/test-only/select-user`

More example request bodies for creating test data are in [doc/CreateData.md](doc/CreateData.md). There is also a Postman collection in the `doc` folder.

## Test-only endpoints

The service provides test-only endpoints for loading and removing account data used by the service and its downstream dependencies.

### GET `/protect-tax-info/test-only/select-user`

Shows preset user scenarios for Government Gateway and One Login accounts, including:

* single account with no enrolments
* single account with IR-SA enrolment
* multiple accounts with no enrolments
* multiple accounts with varying enrolments between none, HMRC-PT, and IR-SA
* accounts with MTD IT enrolments

### GET `/protect-tax-info/test-only/insert-user`

Provides a UI for entering user data in JSON format.

### POST `/protect-tax-info/test-only/create`

Accepts the same JSON format as `insert-user`, deletes any existing records for the account, and then creates the supplied data.

| Status code | Description |
| --- | --- |
| 200 | Account successfully added |
| 500 | Unrecoverable error, possibly invalid JSON submitted |

### POST `/protect-tax-info/test-only/delete`

Accepts the same JSON format and deletes the supplied accounts, enrolments, and related data.

| Status code | Description |
| --- | --- |
| 200 | Account successfully deleted |
| 500 | Unrecoverable error, possibly invalid JSON submitted |

### POST `/protect-tax-info/test-only/insert-user`

Accepts user data in JSON format and inserts the supplied accounts and enrolments. Differs from `create` in that it does not delete existing records first.

| Status code | Description |
| --- | --- |
| 200 | Account successfully inserted |
| 400 | Invalid JSON submitted |
| 500 | Unrecoverable error |

### POST `/protect-tax-info/test-only/success`

Submits a pre-selected test user scenario (referenced by form submission from `select-user` page).

| Status code | Description |
| --- | --- |
| 200 | User data successfully loaded |
| 400 | Invalid form submission |
| 500 | Unrecoverable error |

### GET `/protect-tax-info/test-only/successful`

Verifies that the current authenticated user has the HMRC-PT enrolment assigned to their credential ID. Used to validate successful journey completion.

| Status code | Description |
| --- | --- |
| 200 | User has HMRC-PT enrolment assigned to their credential |
| 500 | User does not have HMRC-PT enrolment or authentication error |

## API

| Path | Supported methods | Type | Description |
| --- | --- | --- | --- |
| `/protect-tax-info?redirectUrl=<urlHere>` | GET | Prod | Main endpoint for users to start their journey |

## Acronyms

In the context of this service we use the following acronyms:

* [API]: Application Programming Interface
* [JRE]: Java Runtime Environment
* [JSON]: JavaScript Object Notation
* [EACD]: Enrolment and Access Control Data (the enrolment store)
* MTD IT: Making Tax Digital for Income Tax
* PT: Personal Tax (the HMRC-PT enrolment)
* SA: Self Assessment (the IR-SA enrolment)
* TENs: Tax Enrolment Assignment Frontend (this service)
* [URL]: Uniform Resource Locator
* [UGS]: Users Groups Search

## License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

[Apache 2.0 License]: http://www.apache.org/licenses/LICENSE-2.0.html
[API]: https://en.wikipedia.org/wiki/Application_programming_interface
[JRE]: http://www.oracle.com/technetwork/java/javase/overview/index.html
[JSON]: http://json.org/
[EACD]: https://github.com/hmrc/enrolment-store-proxy
[URL]: https://en.wikipedia.org/wiki/Uniform_Resource_Locator
[UGS]: https://github.com/hmrc/users-groups-search
