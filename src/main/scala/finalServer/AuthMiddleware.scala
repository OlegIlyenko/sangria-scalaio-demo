package finalServer

import sangria.execution.{FieldTag, MiddlewareBeforeField, MiddlewareQueryContext}
import sangria.schema.Context

case object Authorized extends FieldTag

case class AuthException(message: String) extends Exception(message)

object AuthMiddleware extends MiddlewareBeforeField[AppContext] {
  type QueryVal = Unit
  type FieldVal = Unit

  def beforeQuery(context: MiddlewareQueryContext[AppContext, _, _]) = ()
  def afterQuery(queryVal: QueryVal, context: MiddlewareQueryContext[AppContext, _, _]) = ()

  def beforeField(queryVal: QueryVal, mctx: MiddlewareQueryContext[AppContext, _, _], c: Context[AppContext, _]) = {
    val requiresAuth = c.field.tags.contains(Authorized)

    if (requiresAuth && c.ctx.authToken.isEmpty)
      throw AuthException("Unauthorized access")

    continue
  }
}
