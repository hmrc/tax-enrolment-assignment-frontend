package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.audit

import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails

case class ReportAuditDetailsData(currentAccount: AccountDetails,
                                  reportedAccount: AccountDetails,
                                  saAccount: Option[AccountDetails] = None,
                                  currentAccountType: String)
