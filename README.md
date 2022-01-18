
# Tax Enrolment Assignment Frontend

## Development Setup
- Download rancher: `brew install rancher`
  - Select the latest kubenetes version
  - Select dockerd (moby) container runtime
  - In console..
    >docker run --restart unless-stopped -d -p 27017-27019:27017-27019 --name mongodb mongo:4.2.18

     *(please use latest version as per MDTP best practices, this is just an example)*
  
- Download postgres: `brew install postgresql`
  - As the postgresSQL needs to be initiated prior running tests locally
    - In console..
      >brew services restart postgres
    
      *(please run before executing tests)*
- Run locally: `sbt run` which runs on port `7750` by default
- Run with test endpoints: `run.sh` or `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

## Tests
Run local tests here utilising plugins and coverage requirements `sbt clean coverage test it:test coverageReport scalastyle`

Run Journey Tests: see [here](https://github.com/hmrc/tax-enrolment-assignment-journey-tests)

Run Performance Tests see [here](https://github.com/hmrc/tax-enrolment-assignment-performance-tests)

## API

| Path - internal routes prefixed by `/tax-enrolment-assignment-frontend` | Supported Methods | Type | Description |
|-------|:-------------------:|:-------|-------------|
|`/account-check`| GET | Internal | Endpoint get account check. |
|`/multiple-accounts-check`| GET | Internal | Endpoint to get multiple accounts check. |
|`/tax-enrolment-assignment-frontend/test-only/successful `| GET | Test | Endpoint to get a successful redirect. |
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").