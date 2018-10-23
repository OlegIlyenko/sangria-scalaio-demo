package fullServer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import sangria.execution.{ExceptionHandler, Executor, HandledException, QueryReducer}
import sangria.marshalling.circe._
import sangria.slowlog.SlowLog
import common._
import model.InMemoryDbRepo

object FullServer extends App {
  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val repo = InMemoryDbRepo.createDatabase

  val reducers = List(
    QueryReducer.rejectMaxDepth[Any](15),
    QueryReducer.rejectComplexQueries[Any](400, (complexity, _) ⇒
      new IllegalStateException(s"Too complex query: $complexity/400")))

  val exceptionHandler = ExceptionHandler {
    case (_, AuthException(message)) ⇒ HandledException(message)
  }

  val route = GraphQLRoutes.route { (query, operationName, variables, authToken, tracing) ⇒
    val context = new AppContext(repo, repo, authToken)

    Executor.execute(SchemaDefinition.schema, query, context,
      variables = variables,
      operationName = operationName,
      queryReducers = reducers,
      exceptionHandler = exceptionHandler,
      middleware = AuthMiddleware :: (if (tracing) SlowLog.apolloTracing :: Nil else Nil),
      deferredResolver = context.deferredResolver)
  }

  Http().bindAndHandle(route, "0.0.0.0", sys.props.get("http.port").fold(8080)(_.toInt))
}
