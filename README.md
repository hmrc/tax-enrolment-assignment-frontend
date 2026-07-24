# Tax Enrolment Assignment Frontend

This service provides the frontend endpoint for the Tax Enrolment Assignment journey. It lets users protect tax information and manages the redirects used to connect users with downstream services and test-only journeys.

## Summary

This service provides:

* the main `/protect-tax-info` journey entry point
* test-only endpoints for creating and deleting user and enrolment data
* support for Government Gateway and One Login test journeys
* redirect validation for the URLs used by downstream services

## Requirements

This service is written in [Scala 3.x](http://www.scala-lang.org/) and [Play 3.x](http://playframework.com/), so needs at least a [JRE 21](http://www.oracle.com/technetwork/java/javase/downloads/index.html) to run.

## How to test the project

### Unit tests

* Run the full unit test suite: `sbt test`
* Run a single spec file: `sbt "test:testOnly *fileName"`  
  For example: `sbt "test:testOnly *AccountCheckControllerSpec"`

### Integration tests

* Run the integration test suite: `sbt it/test`

### Acceptance tests

To verify the acceptance tests locally:

1. Start the sm2 container for the TAI profile: `sm2 --start TAI_ALL`
2. Stop the `TAI_FRONTEND` process running in sm2: `sm2 --stop TAI_FRONTEND`
3. Run the service with test endpoints: `sbt "run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"`
4. In the `tai-acceptance-test-suite` repository, run: `./run_specs_local.sh`

## Development setup

To walk the journey locally, run the service with test endpoints:

`sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

Then use the test-only user selection page:

`http://localhost:7750/protect-tax-info/test-only/select-user`

## Test-only endpoints

The service provides test-only endpoints for loading and removing account data used by the service and its downstream dependencies.

### GET `/protect-tax-info/test-only/select-user`

Shows preset user scenarios for Government Gateway and One Login accounts, including:

* single account with no enrolments
* single account with IR-SA enrolment
* multiple accounts with no enrolments
* multiple accounts with varying enrolments between none, HMRC-PT, and IR-SA

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

## API

| Path | Supported methods | Type | Description |
| --- | --- | --- | --- |
| `/protect-tax-info?redirectUrl=<urlHere>` | GET | Prod | Main endpoint for users to start their journey |

## Acronyms

In the context of this service we use the following acronyms:

* [API]: Application Programming Interface
* [JRE]: Java Runtime Environment
* [JSON]: JavaScript Object Notation
* [NPS]: National Insurance and PAYE Service
* [URL]: Uniform Resource Locator
* [UGS]: Users Groups Search

## License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

[Apache 2.0 License]: http://www.apache.org/licenses/LICENSE-2.0.html
[API]: https://en.wikipedia.org/wiki/Application_programming_interface
[JRE]: http://www.oracle.com/technetwork/java/javase/overview/index.html
[JSON]: http://json.org/
[NPS]: http://www.publications.parliament.uk/pa/cm201012/cmselect/cmtreasy/731/73107.htm
[URL]: https://en.wikipedia.org/wiki/Uniform_Resource_Locator
[UGS]: https://github.com/hmrc/users-groups-search
