package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.mvc.{ActionBuilder, AnyContent}

import javax.inject.Inject

class AuthJourney @Inject() (authAction: AuthAction, ptMismatchCheckAction: PTMismatchCheckAction) {

  val authWithPTMismatchCheck: ActionBuilder[RequestWithUserDetailsFromSession, AnyContent] = authAction andThen ptMismatchCheckAction

}
