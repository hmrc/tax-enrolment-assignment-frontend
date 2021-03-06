
# Tax Enrolment Assignment Frontend
## How it works
https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?spaceKey=TEN&title=How+Tax+Enrolment+Assignment+Frontend+works

## For a NEW service to requiring to integrate with us 
- YOU MUST, get in contact with the team that owns this service, we will need to increase our performance tests JPS to take into account your additional users being sent to us.
- We would advise integrating in QA, see https://confluence.tools.tax.service.gov.uk/display/TEN/Journey+Testing+-+Data+setup+requirements#JourneyTestingDatasetuprequirements-SimplestIntegrationUserQA
- They must start their journey on  `/protect-tax-info?redirectUrl=<providing-a-url-desitination-of-where-users-should-be-redirected-to>`
- where redirectUrl should be URL encoded such as `/protect-tax-info?redirectUrl=%2FurlHere`

## Development Setup For Local Unit and IT tests
- Download rancher: `brew install rancher`
  - Select the latest kubenetes version
  - Select dockerd (moby) container runtime
  - In console..
    >docker run --restart unless-stopped -d -p 27017-27019:27017-27019 --name mongodb mongo:4.2.18

     *(please use latest version as per MDTP best practices, this is just an example)*

## Development Setup for LOCAL RUNNING (to walk the journey)
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

| Path                                                         | Supported Methods | Type | Description                                             |
|--------------------------------------------------------------|:-----------------:|:-----|---------------------------------------------------------|
| `/protect-tax-info/redirectUrl=<urlHere>`                    |        GET        | Prod | Main endpoint for users to start their journey          |
| `/protect-tax-info/test-only/successful`                     |        GET        | Test | Endpoint to get a successful redirect.                  |
| `/users-groups-search/test-only/users/:credId`               |        GET        | Test | Mimic UGS locally and in staging.                       |
| `/protect-tax-info/test-only/auth/enrolments`                |        GET        | Test | Return enrolments in session from auth                  |
| `/add-taxes-frontend/test-only/self-assessment/enrol-for-sa` |       POST        | Test | Mimic Add taxes frontend enrol for sa endpoint          |
| `/sa/test-only/start`                                        |        GET        | Test | Mimic Endpoint returned from Add taxes frontend in JSON |

## Throttling

- controlled via one config value `throttle.percentage`
- allocates temporary PTA enrolment (to Auth not ESP)
- applies to certain account types
- set to 0 to disable
- set to 1 to throttle NINOs with pattern QQ112200Q
- set to 100 to throttle NINOS with pattern QQ112299Q
- https://confluence.tools.tax.service.gov.uk/display/TEN/Throttling+processes

## Audits
https://confluence.tools.tax.service.gov.uk/display/TEN/CIP+Assessment+tracker+-+TENINO

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
