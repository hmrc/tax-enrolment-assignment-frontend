
# Tax Enrolment Assignment Frontend
## How it works
https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?spaceKey=TEN&title=How+Tax+Enrolment+Assignment+Frontend+works

## For a NEW service to requiring to integrate with us 
- YOU MUST, get in contact with the team that owns this service, we will need to allow the http host if needed and increase our performance tests JPS to take into account your additional users being sent to us.
- They must start their journey on  `/protect-tax-info?redirectUrl=<providing-a-url-desitination-of-where-users-should-be-redirected-to>`
- where redirectUrl should be URL encoded such as `/protect-tax-info?redirectUrl=%2FurlHere`

## Development Setup for LOCAL RUNNING (to walk the journey)
- Run with test endpoints: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`
- use the supporting endpoint https://localhost:7750/protect-tax-info/test-only/select-user.

## Tests
Run local tests here utilising plugins and coverage requirements `sbt clean coverage test it/test coverageReport scalastyle`

Run Journey Tests: see [here](https://github.com/hmrc/tax-enrolment-assignment-journey-tests)

Run Performance Tests see [here](https://github.com/hmrc/tax-enrolment-assignment-performance-tests)

## Test Only Endpoints
There are 3 test only endpoints that can be used to add test data to use the service and all downstream APIs that the service relies upon such as enrolment store, BAS stubs, one-login stubs etc. And one to delete from all the same APIs. 2 are UI based and the others are POST routes

### GET /protect-tax-info/test-only/select-user
The select-user endpoint presents a page with 22 preset user details, with a range of scenarios for both Government Gateway and One Login users, these include:
- Single Account with no enrolments
- Single Account with IR-SA enrolment
- Multiple Accounts with no enrolments
- Multiple Account, each with varying enrolments between none, HMRC-PT and IR-SA

After selecting and succesfully submitting a user, you will be taken to a success page that will let you easily log in to the service with any of the accounts added.

### GET /protect-tax-info/test-only/insert-user
The insert-user end-point provides a more open UI solution to enter data, here you can input data in the text area in the following format, which is an example of a user with one account with an IR-SA enrolment:
```json
[
{
  "nino":"<nino>",
  "affinityGroup":"Individual",
  "additionalFactors":[
    {
      "factorType":"totp",
      "name":"HMRC App"
    }],
  "groupId":"<group-id>",
  "enrolments":[{
    "verifiers":[
      {
        "key":"Postcode",
        "value":"postcode"
      }, {
        "key":"NINO",
        "value":"<nino>"
      }],
    "state":"Activated",
    "serviceName":"IR-SA",
    "identifiers":{
      "key":"UTR",
      "value":"<UTR-value>"
    },
    "enrolmentType":"principal",
    "enrolmentFriendlyName":"IR-SA Enrolment"
  }],
  "identityProviderType":"<Login Provider Value>",
  "user":{
    "credId":"<cred-id>",
    "name":"Firstname Surname", 
    "email":"email9@test.com", 
    "description":"Description", 
    "credentialRole":"Admin"
  }
}]
```

### POST /protect-tax-info/test-only/create
The /create POST route will accept the same JSON format as the /insert-data route listed above and can be used as a more direct way of inserting test data, it will first delete the records of the account given, then submit all the data.

| StatusCode | Description                                                                        |
|------------|------------------------------------------------------------------------------------|
| 200        | Account successfully added                                                         |
| 500        | There was an unrecoverable error, possibly invalid JSON submitted

### POST /protect-tax-info/test-only/delete
POST /delete will accept the same JSON format account data as the two above routes and will delete all of the accounts, enrolments and related data that is provided

| StatusCode | Description                                                       |
|------------|-------------------------------------------------------------------|
| 200        | Account successfully deleted                                      |
| 500        | There was an unrecoverable error, possibly invalid JSON submitted


## API

| Path                                                         | Supported Methods | Type | Description                                             |
|--------------------------------------------------------------|:-----------------:|:-----|---------------------------------------------------------|
| `/protect-tax-info/redirectUrl=<urlHere>`                    |        GET        | Prod | Main endpoint for users to start their journey          |

## Audits
https://confluence.tools.tax.service.gov.uk/display/TEN/CIP+Assessment+tracker+-+TENINO

## Encryption
The result of UGS contains an email address which we display to the user, we encrypt this when we store it in mongo

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
