All the data in the different stubs can be created from the test only endpoint `POST /protect-tax-info/test-only/create`

The request body describes the different accounts, enrolments and users necessary for testing.

## Examples request Body
### A single account with no enrolments
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

### Multiples accounts with enrolments and mfas
```json
[
  {
    "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
    "affinityGroup": "Individual",
    "nino": "<nino>",
    "user": {
      "credId": "5217739547427626",
      "name": "Firstname Surname",
      "email": "email@example.invalid",
      "credentialRole": "Admin",
      "description": "Description"
    },
    "enrolments": [
      {
        "serviceName": "IR-SA",
        "assignedUserCreds": [
          "1"
        ],
        "identifiers":
        {
          "key": "UTR",
          "value": "123456"
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
    "nino": "$nino",
    "user": {
      "credId": "5217739547427627",
      "name": "Firstname Surname",
      "email": "email@example.invalid",
      "credentialRole": "Admin",
      "description": "Description"
    },
    "additionalFactors": [
      {
        "factorType": "factorType",
        "phoneNumber": "Phone number",
        "name": "name"
      }
    ]
  }
]
```