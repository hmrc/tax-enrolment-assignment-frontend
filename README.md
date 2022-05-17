
# Tax Enrolment Assignment Frontend

## Development Setup For Local Unit and IT tests
- Download rancher: `brew install rancher`
  - Select the latest kubenetes version
  - Select dockerd (moby) container runtime
  - In console..
    >docker run --restart unless-stopped -d -p 27017-27019:27017-27019 --name mongodb mongo:4.2.18

     *(please use latest version as per MDTP best practices, this is just an example)*


## Development Setup for local running
- please complete all pre-requisites within https://github.com/hmrc/tax-enrolment-assignment-journey-tests readme
- Run locally: `sbt run` which runs on port `7750` by default
- OR Run with test endpoints: `run.sh` or `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`
- please run the https://github.com/hmrc/tax-enrolment-assignment-journey-tests tests before manually navigating through app
- pick a user from the local section within https://github.com/hmrc/tax-enrolment-assignment-journey-tests/blob/main/src/test/resources/environments.conf
- Navigate to http://localhost:9949/auth-login-stub/gg-sign-in and enter all user details from the environment conf values.
  - Setting the RedirectURL to `http://localhost:7750/protect-tax-info?redirectUrl=http%3A%2F%2Flocalhost%3A7750%2Fprotect-tax-info%2Ftest-only%2Fsuccessful`
  - Confidence Level to 200

See https://confluence.tools.tax.service.gov.uk/display/TEN/Journey+Testing+-+Data+setup+requirements for more info
## Tests
Run local tests here utilising plugins and coverage requirements `sbt clean coverage test it:test coverageReport scalastyle`

Run Journey Tests: see [here](https://github.com/hmrc/tax-enrolment-assignment-journey-tests)

Run Performance Tests see [here](https://github.com/hmrc/tax-enrolment-assignment-performance-tests)



## API

| Path - internal routes prefixed by `/protect-tax-info` | Supported Methods | Type | Description                                   |
|--------------------------------------------------------|:-------------------:|:-------|-----------------------------------------------|
| `/account-check`                                       | GET | Internal | Endpoint get account check.                   |
| `/multiple-accounts-check`                             | GET | Internal | Endpoint to get multiple accounts check.      |
| `/protect-tax-info/test-only/successful `              | GET | Test | Endpoint to get a successful redirect.        |
| `/protect-tax-info/test-only/auth/enrolments `         | GET | Test | Endpoint to get enrolments from auth          |
| `/users-groups-search/test-only/users/:credId `         | GET | Test | Endpoint to mimic User groups search endpoint |


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
