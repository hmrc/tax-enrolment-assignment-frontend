
# Tax Enrolment Assignment Frontend
## How it works
https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?spaceKey=TEN&title=How+Tax+Enrolment+Assignment+Frontend+works

## For a NEW service to requiring to integrate with us 
- YOU MUST, get in contact with the team that owns this service, we will need to increase our performance tests JPS to take into account your additional users being sent to us.
- We would advise integrating in QA, see https://confluence.tools.tax.service.gov.uk/display/TEN/Journey+Testing+-+Data+setup+requirements#JourneyTestingDatasetuprequirements-SimplestIntegrationUserQA
- They must start their journey on  `/protect-tax-info?redirectUrl=<providing-a-url-desitination-of-where-users-should-be-redirected-to>`
- where redirectUrl should be URL encoded such as `/protect-tax-info?redirectUrl=%2FurlHere`

## Development Setup for LOCAL RUNNING (to walk the journey)
- Run with test endpoints: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`
- use the supporting endpoint `POST /protect-tax-info/test-only/create` to [create data](doc/CreateData.md).
- Navigate to http://localhost:9949/auth-login-stub/gg-sign-in and enter all user details matching the data inserted above.
  - Setting the RedirectURL to `http://localhost:7750/protect-tax-info?redirectUrl=http%3A%2F%2Flocalhost%3A7750%2Fprotect-tax-info%2Ftest-only%2Fsuccessful`
  - Confidence Level to 200

See https://confluence.tools.tax.service.gov.uk/display/TEN/Journey+Testing+-+Data+setup+requirements for more info
 
## Tests
Run local tests here utilising plugins and coverage requirements `sbt clean coverage test it/test coverageReport scalastyle`

Run Journey Tests: see [here](https://github.com/hmrc/tax-enrolment-assignment-journey-tests)

Run Performance Tests see [here](https://github.com/hmrc/tax-enrolment-assignment-performance-tests)

## API

| Path                                                         | Supported Methods | Type | Description                                             |
|--------------------------------------------------------------|:-----------------:|:-----|---------------------------------------------------------|
| `/protect-tax-info/redirectUrl=<urlHere>`                    |        GET        | Prod | Main endpoint for users to start their journey          |
| `/protect-tax-info/test-only/successful`                     |        GET        | Test | Endpoint to get a successful redirect.                  |
| `/protect-tax-info/test-only/create`                         |        POST       | Test | Setup data in all the stubs                             |                   

## Audits
https://confluence.tools.tax.service.gov.uk/display/TEN/CIP+Assessment+tracker+-+TENINO

## Encryption
The result of UGS contains an email address which we display to the user, we encrypt this when we store it in mongo

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
