All the data in the different stubs can be created from the test only endpoint `POST /protect-tax-info/test-only/create`

The request body describes the different accounts, enrolments and users necessary for testing.

## Examples request Body
### A single account with no enrolments
Replace <nino> with a valid nino.
groupId and credId can be edited to any values.

```json
    {
        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
        "affinityGroup": "Individual",
        "nino": "<nino>",
        "user": {
            "credId": "5217739547427627",
            "name": "Firstname Surname",
            "email": "email@example.invalid",
            "credentialRole": "Admin",
            "description": "Description"
        }
    }
```

### Two accounts for the same nino with SA enrolment and no HMRC-PT enrolment
Replace <nino> with a valid nino and <factorType> with either `sms` or `totp`.
groupId and credId can be edited to any values.

```json
[
  {
    "groupId": "00ADEA51-C0BA-497D-997E-F585FAADBCEK",
    "affinityGroup": "Individual",
    "nino": "<nino>",
    "user": {
      "credId": "5517739547427626",
      "name": "Firstname Surname",
      "email": "email@example.invalid"
    },
    "enrolments": [
      {
        "serviceName": "IR-SA",
        "identifiers":
        {
          "key": "UTR",
          "value": "64567890"
        }
      ,
        "verifiers": [
          {
            "key": "Postcode",
            "value": "postcode"
          },
          {
            "key": "NINO",
            "value": "<nino>"
          }
        ],
        "enrolmentFriendlyName": "IR-SA Enrolment",
        "state": "Activated",
        "enrolmentType": "principal",
        "assignedToAll": false
      }
    ]
  },
  {
    "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
    "affinityGroup": "Individual",
    "nino": "<nino>",
    "user": {
      "credId": "5217739547427627",
      "name": "Firstname Surname",
      "email": "email@example.invalid"
    },
    "additionalFactors": [
      {
        "factorType": "<factorType>",
        "phoneNumber": "Phone number",
        "name": "name"
      }
    ]
  }
]
```